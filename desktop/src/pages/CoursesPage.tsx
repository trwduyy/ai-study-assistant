import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { BookOpen, Edit2, FileText, Layers, Plus, Trash2, X } from "lucide-react";
import courseService from "../services/courseService";
import { Course } from "../types";
import LoadingSpinner from "../components/common/LoadingSpinner";

export const CoursesPage: React.FC = () => {
    const [courses, setCourses] = useState<Course[]>([]);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    // Modal state
    const [isModalOpen, setIsModalOpen] = useState<boolean>(false);
    const [editingCourse, setEditingCourse] = useState<Course | null>(null);
    const [title, setTitle] = useState<string>("");
    const [description, setDescription] = useState<string>("");
    const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
    const [formError, setFormError] = useState<string | null>(null);

    const fetchCourses = async () => {
        try {
            setIsLoading(true);
            const data = await courseService.getCourses();
            setCourses(data);
            setError(null);
        } catch (err: unknown) {
            if (err instanceof Error) {
                setError(err.message);
            } else {
                setError("Không thể tải danh sách khóa học.");
            }
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        fetchCourses();
    }, []);

    const handleOpenCreateModal = () => {
        setEditingCourse(null);
        setTitle("");
        setDescription("");
        setFormError(null);
        setIsModalOpen(true);
    };

    const handleOpenEditModal = (course: Course, e: React.MouseEvent) => {
        e.stopPropagation();
        e.preventDefault();
        setEditingCourse(course);
        setTitle(course.title);
        setDescription(course.description || "");
        setFormError(null);
        setIsModalOpen(true);
    };

    const handleDeleteCourse = async (id: number, courseTitle: string, e: React.MouseEvent) => {
        e.stopPropagation();
        e.preventDefault();
        if (window.confirm(`Bạn có chắc chắn muốn xóa khóa học "${courseTitle}" và toàn bộ tài liệu bên trong không?`)) {
            try {
                await courseService.deleteCourse(id);
                setCourses((prev) => prev.filter((c) => c.id !== id));
            } catch (err: unknown) {
                alert(err instanceof Error ? err.message : "Xóa khóa học thất bại.");
            }
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setFormError(null);

        if (!title.trim()) {
            setFormError("Vui lòng nhập tên khóa học.");
            return;
        }

        try {
            setIsSubmitting(true);
            if (editingCourse) {
                const updated = await courseService.updateCourse(editingCourse.id, {
                    title: title.trim(),
                    description: description.trim()
                });
                setCourses((prev) =>
                    prev.map((c) => (c.id === updated.id ? { ...c, ...updated } : c))
                );
            } else {
                const created = await courseService.createCourse({
                    title: title.trim(),
                    description: description.trim()
                });
                setCourses((prev) => [created, ...prev]);
            }
            setIsModalOpen(false);
        } catch (err: unknown) {
            setFormError(err instanceof Error ? err.message : "Thao tác thất bại.");
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <div className="space-y-6">
            {/* Header Toolbar */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                    <h2 className="text-xl font-bold text-slate-800">Khóa học của tôi</h2>
                    <p className="text-sm text-slate-500 mt-0.5">
                        Quản lý các môn học, chủ đề học tập và tài liệu đính kèm
                    </p>
                </div>
                <button
                    onClick={handleOpenCreateModal}
                    className="inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-sm shadow-sm transition"
                >
                    <Plus className="w-4 h-4" />
                    <span>Tạo khóa học mới</span>
                </button>
            </div>

            {/* Error Banner */}
            {error && (
                <div className="p-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-sm">
                    {error}
                </div>
            )}

            {/* Content Display */}
            {isLoading ? (
                <LoadingSpinner size="lg" text="Đang tải danh sách khóa học..." className="py-20" />
            ) : courses.length > 0 ? (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    {courses.map((course) => (
                        <Link
                            key={course.id}
                            to={`/courses/${course.id}`}
                            className="group block bg-white rounded-2xl border border-slate-200 hover:border-indigo-300 p-6 shadow-sm hover:shadow-md transition relative flex flex-col justify-between"
                        >
                            <div>
                                <div className="flex items-start justify-between gap-2">
                                    <div className="w-10 h-10 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center shrink-0">
                                        <BookOpen className="w-5 h-5" />
                                    </div>
                                    <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition">
                                        <button
                                            onClick={(e) => handleOpenEditModal(course, e)}
                                            className="p-1.5 rounded-lg text-slate-400 hover:text-indigo-600 hover:bg-indigo-50"
                                            title="Sửa khóa học"
                                        >
                                            <Edit2 className="w-4 h-4" />
                                        </button>
                                        <button
                                            onClick={(e) => handleDeleteCourse(course.id, course.title, e)}
                                            className="p-1.5 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50"
                                            title="Xóa khóa học"
                                        >
                                            <Trash2 className="w-4 h-4" />
                                        </button>
                                    </div>
                                </div>

                                <h3 className="mt-4 text-base font-bold text-slate-800 group-hover:text-indigo-600 transition line-clamp-1">
                                    {course.title}
                                </h3>
                                <p className="mt-1 text-xs text-slate-500 line-clamp-2 min-h-[2rem]">
                                    {course.description || "Chưa có mô tả cho khóa học này."}
                                </p>
                            </div>

                            <div className="mt-6 pt-4 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500">
                                <span className="flex items-center gap-1.5">
                                    <Layers className="w-4 h-4 text-slate-400" />
                                    <span>{course.topicCount} chủ đề</span>
                                </span>
                                <span className="flex items-center gap-1.5">
                                    <FileText className="w-4 h-4 text-slate-400" />
                                    <span>{course.materialCount} tài liệu</span>
                                </span>
                            </div>
                        </Link>
                    ))}
                </div>
            ) : (
                <div className="bg-white rounded-2xl border border-dashed border-slate-300 p-12 text-center">
                    <div className="w-16 h-16 rounded-2xl bg-indigo-50 text-indigo-600 flex items-center justify-center mx-auto mb-4">
                        <BookOpen className="w-8 h-8" />
                    </div>
                    <h3 className="text-base font-bold text-slate-800">Bạn chưa có khóa học nào</h3>
                    <p className="text-sm text-slate-500 max-w-sm mx-auto mt-1 mb-6">
                        Tạo khóa học đầu tiên để bắt đầu tải lên tài liệu học tập và khai thác sức mạnh của AI.
                    </p>
                    <button
                        onClick={handleOpenCreateModal}
                        className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-sm shadow-sm transition"
                    >
                        <Plus className="w-4 h-4" />
                        <span>Tạo khóa học ngay</span>
                    </button>
                </div>
            )}

            {/* Create/Edit Modal */}
            {isModalOpen && (
                <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-sm flex items-center justify-center p-4">
                    <div className="bg-white rounded-2xl shadow-xl w-full max-w-md overflow-hidden border border-slate-200">
                        <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between">
                            <h3 className="font-bold text-slate-800 text-base">
                                {editingCourse ? "Chỉnh sửa khóa học" : "Tạo khóa học mới"}
                            </h3>
                            <button
                                onClick={() => setIsModalOpen(false)}
                                className="text-slate-400 hover:text-slate-600 p-1 rounded-lg"
                            >
                                <X className="w-5 h-5" />
                            </button>
                        </div>

                        <form onSubmit={handleSubmit} className="p-6 space-y-4">
                            {formError && (
                                <div className="p-3 rounded-lg bg-rose-50 text-rose-700 text-xs font-medium">
                                    {formError}
                                </div>
                            )}

                            <div>
                                <label className="block text-xs font-semibold text-slate-700 mb-1.5 uppercase">
                                    Tên khóa học <span className="text-rose-500">*</span>
                                </label>
                                <input
                                    type="text"
                                    value={title}
                                    onChange={(e) => setTitle(e.target.value)}
                                    placeholder="Ví dụ: Cơ sở dữ liệu, Trí tuệ nhân tạo..."
                                    required
                                    className="w-full px-3.5 py-2 rounded-xl border border-slate-300 focus:outline-none focus:ring-2 focus:ring-indigo-500 text-sm text-slate-800"
                                />
                            </div>

                            <div>
                                <label className="block text-xs font-semibold text-slate-700 mb-1.5 uppercase">
                                    Mô tả khóa học
                                </label>
                                <textarea
                                    value={description}
                                    onChange={(e) => setDescription(e.target.value)}
                                    placeholder="Tóm tắt nội dung mục tiêu của khóa học này..."
                                    rows={3}
                                    className="w-full px-3.5 py-2 rounded-xl border border-slate-300 focus:outline-none focus:ring-2 focus:ring-indigo-500 text-sm text-slate-800"
                                />
                            </div>

                            <div className="pt-2 flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={() => setIsModalOpen(false)}
                                    className="px-4 py-2 rounded-xl border border-slate-200 text-slate-600 hover:bg-slate-50 text-sm font-medium transition"
                                >
                                    Hủy
                                </button>
                                <button
                                    type="submit"
                                    disabled={isSubmitting}
                                    className="px-5 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold shadow-sm transition disabled:opacity-60"
                                >
                                    {isSubmitting ? "Đang lưu..." : editingCourse ? "Cập nhật" : "Tạo mới"}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default CoursesPage;
