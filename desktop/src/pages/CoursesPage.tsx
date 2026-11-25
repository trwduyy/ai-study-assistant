import React, { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
    BookOpen,
    Calendar,
    ChevronRight,
    Edit2,
    FileText,
    Layers,
    Plus,
    Search,
    Sparkles,
    Trash2,
    X
} from "lucide-react";
import learningService from "../services/learningService";
import { Course } from "../types";
import { Button } from "../components/ui/button";
import { Input } from "../components/ui/input";
import { Card, CardContent } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { EmptyState } from "../components/common/EmptyState";
import { LoadingState } from "../components/common/LoadingState";
import { ErrorBanner } from "../components/common/ErrorBanner";
import { Pagination } from "../components/ui/pagination";
import { toast } from "sonner";

export const CoursesPage: React.FC = () => {
    const navigate = useNavigate();
    const [courses, setCourses] = useState<Course[]>([]);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    // Search & Pagination
    const [searchTerm, setSearchTerm] = useState<string>("");
    const [currentPage, setCurrentPage] = useState<number>(0);
    const [totalPages, setTotalPages] = useState<number>(1);
    const pageSize = 12;

    // Modal state
    const [isModalOpen, setIsModalOpen] = useState<boolean>(false);
    const [editingCourse, setEditingCourse] = useState<Course | null>(null);
    const [courseName, setCourseName] = useState<string>("");
    const [description, setDescription] = useState<string>("");
    const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
    const [formError, setFormError] = useState<string | null>(null);

    const fetchCourses = async (page = 0) => {
        try {
            setIsLoading(true);
            const result = await learningService.getCourses(page, pageSize);
            setCourses(result.items);
            if (result.meta?.totalPages) {
                setTotalPages(result.meta.totalPages);
            }
            setCurrentPage(page);
            setError(null);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "Không thể tải danh sách khóa học.");
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        void fetchCourses(0);
    }, []);

    const handleOpenCreateModal = () => {
        setEditingCourse(null);
        setCourseName("");
        setDescription("");
        setFormError(null);
        setIsModalOpen(true);
    };

    const handleOpenEditModal = (course: Course, e: React.MouseEvent) => {
        e.stopPropagation();
        e.preventDefault();
        setEditingCourse(course);
        setCourseName(course.name);
        setDescription(course.description || "");
        setFormError(null);
        setIsModalOpen(true);
    };

    const handleDeleteCourse = async (course: Course, e: React.MouseEvent) => {
        e.stopPropagation();
        e.preventDefault();
        const confirmed = window.confirm(
            `Bạn có chắc chắn muốn xóa khóa học "${course.name}" và toàn bộ tài liệu bên trong không?`
        );
        if (!confirmed) return;

        try {
            await learningService.deleteCourse(course.id);
            toast.success(`Đã xóa khóa học "${course.name}"`);
            setCourses((prev) => prev.filter((c) => c.id !== course.id));
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Xóa khóa học thất bại.");
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setFormError(null);

        if (!courseName.trim()) {
            setFormError("Vui lòng nhập tên khóa học.");
            return;
        }

        try {
            setIsSubmitting(true);
            if (editingCourse) {
                const updated = await learningService.updateCourse(editingCourse.id, {
                    name: courseName.trim(),
                    description: description.trim() || undefined,
                    version: editingCourse.version
                });
                setCourses((prev) =>
                    prev.map((c) => (c.id === updated.id ? { ...c, ...updated } : c))
                );
                toast.success("Cập nhật khóa học thành công!");
            } else {
                const created = await learningService.createCourse({
                    name: courseName.trim(),
                    description: description.trim() || undefined
                });
                setCourses((prev) => [created, ...prev]);
                toast.success("Tạo khóa học mới thành công!");
            }
            setIsModalOpen(false);
        } catch (err: unknown) {
            setFormError(err instanceof Error ? err.message : "Thao tác thất bại.");
        } finally {
            setIsSubmitting(false);
        }
    };

    // Filter courses locally by search term
    const filteredCourses = courses.filter((c) =>
        c.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
        (c.description && c.description.toLowerCase().includes(searchTerm.toLowerCase()))
    );

    return (
        <div className="space-y-6">
            {/* Header Toolbar */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                    <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Khóa học & Tài liệu học tập</h1>
                    <p className="text-sm text-slate-500 mt-1">
                        Quản lý môn học, cấu trúc chương mục và không gian học tập tích hợp AI
                    </p>
                </div>
                <div className="flex items-center gap-3">
                    <Button
                        variant="outline"
                        onClick={() => navigate("/flashcards")}
                        className="gap-2"
                    >
                        <Layers className="w-4 h-4 text-emerald-600" />
                        <span>Bộ thẻ Flashcard</span>
                    </Button>
                    <Button onClick={handleOpenCreateModal} className="gap-2">
                        <Plus className="w-4 h-4" />
                        <span>Tạo khóa học mới</span>
                    </Button>
                </div>
            </div>

            {/* Search Bar */}
            <div className="flex items-center gap-3">
                <div className="relative flex-1 max-w-md">
                    <Search className="w-4 h-4 absolute left-3 top-3 text-slate-400" />
                    <Input
                        type="text"
                        placeholder="Tìm kiếm theo tên khóa học hoặc mô tả..."
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        className="pl-9"
                    />
                </div>
            </div>

            {/* Error Banner */}
            {error && (
                <ErrorBanner
                    title="Không thể tải khóa học"
                    message={error}
                    onRetry={() => fetchCourses(currentPage)}
                />
            )}

            {/* Loading State */}
            {isLoading ? (
                <LoadingState text="Đang tải danh sách khóa học của bạn..." />
            ) : filteredCourses.length === 0 ? (
                <EmptyState
                    icon={<BookOpen className="w-8 h-8 text-slate-400" />}
                    title={searchTerm ? "Không tìm thấy khóa học" : "Chưa có khóa học nào"}
                    description={
                        searchTerm
                            ? `Không có kết quả nào phù hợp với từ khóa "${searchTerm}".`
                            : "Bắt đầu bằng cách tạo khóa học đầu tiên để tải tài liệu và sinh bài tập AI."
                    }
                    action={
                        !searchTerm ? (
                            <Button onClick={handleOpenCreateModal} className="gap-2">
                                <Plus className="w-4 h-4" />
                                <span>Tạo khóa học ngay</span>
                            </Button>
                        ) : undefined
                    }
                />
            ) : (
                <>
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                        {filteredCourses.map((course) => (
                            <Card
                                key={course.id}
                                className="group hover:border-primary/40 hover:shadow-elevated transition-all flex flex-col justify-between"
                            >
                                <CardContent className="p-6">
                                    <div className="flex items-start justify-between gap-3 mb-3">
                                        <div className="w-10 h-10 rounded-lg bg-blue-50 text-primary flex items-center justify-center shrink-0 border border-blue-100">
                                            <BookOpen className="w-5 h-5" />
                                        </div>
                                        <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                                            <Button
                                                variant="ghost"
                                                size="sm"
                                                onClick={(e) => handleOpenEditModal(course, e)}
                                                className="h-8 w-8 p-0 text-slate-500 hover:text-slate-800"
                                                title="Sửa khóa học"
                                            >
                                                <Edit2 className="w-3.5 h-3.5" />
                                            </Button>
                                            <Button
                                                variant="ghost"
                                                size="sm"
                                                onClick={(e) => handleDeleteCourse(course, e)}
                                                className="h-8 w-8 p-0 text-rose-500 hover:text-rose-700"
                                                title="Xóa khóa học"
                                            >
                                                <Trash2 className="w-3.5 h-3.5" />
                                            </Button>
                                        </div>
                                    </div>

                                    <Link to={`/courses/${course.id}`} className="block">
                                        <h3 className="font-semibold text-slate-900 group-hover:text-primary transition-colors text-base line-clamp-1">
                                            {course.name}
                                        </h3>
                                        <p className="text-xs text-slate-500 mt-1 line-clamp-2 min-h-[32px]">
                                            {course.description || "Chưa có mô tả cho khóa học này."}
                                        </p>
                                    </Link>

                                    <div className="mt-4 pt-4 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500">
                                        <div className="flex items-center gap-3">
                                            <span className="flex items-center gap-1 font-medium text-slate-700">
                                                <Layers className="w-3.5 h-3.5 text-slate-400" />
                                                <span>{course.chapterCount ?? 0} chương</span>
                                            </span>
                                            <span className="flex items-center gap-1 font-medium text-slate-700">
                                                <FileText className="w-3.5 h-3.5 text-slate-400" />
                                                <span>{course.materialCount ?? 0} tài liệu</span>
                                            </span>
                                        </div>
                                        <Link
                                            to={`/courses/${course.id}`}
                                            className="inline-flex items-center gap-1 font-semibold text-primary hover:text-primary-hover transition"
                                        >
                                            <span>Chi tiết</span>
                                            <ChevronRight className="w-3.5 h-3.5" />
                                        </Link>
                                    </div>
                                </CardContent>
                            </Card>
                        ))}
                    </div>

                    {totalPages > 1 && (
                        <div className="pt-4 flex justify-center">
                            <Pagination
                                currentPage={currentPage}
                                totalPages={totalPages}
                                onPageChange={(p) => fetchCourses(p)}
                            />
                        </div>
                    )}
                </>
            )}

            {/* Create / Edit Modal */}
            {isModalOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4 animate-in fade-in">
                    <div className="w-full max-w-md bg-white rounded-2xl shadow-elevated border border-slate-200 overflow-hidden">
                        <div className="p-6 border-b border-slate-100 flex items-center justify-between">
                            <h2 className="text-lg font-bold text-slate-900">
                                {editingCourse ? "Cập nhật khóa học" : "Tạo khóa học mới"}
                            </h2>
                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => setIsModalOpen(false)}
                                className="h-8 w-8 p-0"
                            >
                                <X className="w-4 h-4" />
                            </Button>
                        </div>

                        <form onSubmit={handleSubmit} className="p-6 space-y-4">
                            {formError && (
                                <div className="p-3 bg-rose-50 border border-rose-200 rounded-lg text-xs text-rose-700">
                                    {formError}
                                </div>
                            )}

                            <div>
                                <label className="block text-xs font-semibold uppercase text-slate-600 mb-1.5">
                                    Tên môn học / Khóa học <span className="text-rose-500">*</span>
                                </label>
                                <Input
                                    type="text"
                                    placeholder="Ví dụ: Mạng máy tính, An toàn hệ điều hành..."
                                    value={courseName}
                                    onChange={(e) => setCourseName(e.target.value)}
                                    required
                                    autoFocus
                                />
                            </div>

                            <div>
                                <label className="block text-xs font-semibold uppercase text-slate-600 mb-1.5">
                                    Mô tả ngắn gọn
                                </label>
                                <textarea
                                    className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary min-h-[90px]"
                                    placeholder="Nội dung chính, mục tiêu hoặc ghi chú của môn học..."
                                    value={description}
                                    onChange={(e) => setDescription(e.target.value)}
                                />
                            </div>

                            <div className="pt-2 flex items-center justify-end gap-3">
                                <Button
                                    type="button"
                                    variant="outline"
                                    onClick={() => setIsModalOpen(false)}
                                    disabled={isSubmitting}
                                >
                                    Hủy
                                </Button>
                                <Button type="submit" isLoading={isSubmitting}>
                                    {editingCourse ? "Lưu thay đổi" : "Tạo khóa học"}
                                </Button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default CoursesPage;
