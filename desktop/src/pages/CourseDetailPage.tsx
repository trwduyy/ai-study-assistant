import React, { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
    AlertCircle,
    ArrowLeft,
    Award,
    CheckCircle2,
    Clock,
    Download,
    FileText,
    Layers,
    Play,
    Plus,
    Sparkles,
    Trash2,
    UploadCloud,
    X
} from "lucide-react";
import courseService from "../services/courseService";
import materialService from "../services/materialService";
import quizService from "../services/quizService";
import { CourseDetail, Difficulty, Material, Quiz, Topic } from "../types";
import LoadingSpinner from "../components/common/LoadingSpinner";

export const CourseDetailPage: React.FC = () => {
    const { courseId } = useParams<{ courseId: string }>();
    const navigate = useNavigate();
    const id = Number(courseId);

    const [course, setCourse] = useState<CourseDetail | null>(null);
    const [materials, setMaterials] = useState<Material[]>([]);
    const [quizzes, setQuizzes] = useState<Quiz[]>([]);
    const [selectedTopicId, setSelectedTopicId] = useState<number | undefined>(undefined);

    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    // Topic Modal state
    const [isTopicModalOpen, setIsTopicModalOpen] = useState(false);
    const [topicTitle, setTopicTitle] = useState("");
    const [isTopicSubmitting, setIsTopicSubmitting] = useState(false);

    // Material Upload Modal state
    const [isUploadModalOpen, setIsUploadModalOpen] = useState(false);
    const [uploadFile, setUploadFile] = useState<File | null>(null);
    const [uploadTopicId, setUploadTopicId] = useState<number | undefined>(undefined);
    const [uploadProgress, setUploadProgress] = useState<number>(0);
    const [isUploading, setIsUploading] = useState(false);
    const [uploadError, setUploadError] = useState<string | null>(null);

    // Generate Quiz Modal state
    const [isQuizModalOpen, setIsQuizModalOpen] = useState(false);
    const [quizTitle, setQuizTitle] = useState("");
    const [quizDifficulty, setQuizDifficulty] = useState<Difficulty>("MEDIUM");
    const [quizQuestionCount, setQuizQuestionCount] = useState<number>(5);
    const [quizMaterialId, setQuizMaterialId] = useState<number | undefined>(undefined);
    const [isQuizGenerating, setIsQuizGenerating] = useState(false);
    const [quizError, setQuizError] = useState<string | null>(null);

    const fetchAllData = async () => {
        try {
            setIsLoading(true);
            const [courseData, materialsData, quizzesData] = await Promise.all([
                courseService.getCourseDetail(id),
                materialService.getMaterials(id),
                quizService.getQuizzesByCourse(id)
            ]);

            setCourse(courseData);
            setMaterials(materialsData);
            setQuizzes(quizzesData);
            setError(null);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "Không thể tải chi tiết khóa học.");
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        if (!isNaN(id)) {
            fetchAllData();
        }
    }, [id]);

    // Handle Topic Create
    const handleCreateTopic = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!topicTitle.trim()) return;

        try {
            setIsTopicSubmitting(true);
            const created = await courseService.createTopic(id, {
                title: topicTitle.trim(),
                orderIndex: (course?.topics.length || 0) + 1
            });
            setCourse((prev) => (prev ? { ...prev, topics: [...prev.topics, created] } : null));
            setIsTopicModalOpen(false);
            setTopicTitle("");
        } catch (err: unknown) {
            alert(err instanceof Error ? err.message : "Thêm chủ đề thất bại.");
        } finally {
            setIsTopicSubmitting(false);
        }
    };

    // Handle Topic Delete
    const handleDeleteTopic = async (topicId: number, title: string) => {
        if (window.confirm(`Bạn có chắc muốn xóa chủ đề "${title}" không?`)) {
            try {
                await courseService.deleteTopic(id, topicId);
                setCourse((prev) =>
                    prev ? { ...prev, topics: prev.topics.filter((t) => t.id !== topicId) } : null
                );
                if (selectedTopicId === topicId) {
                    setSelectedTopicId(undefined);
                }
            } catch (err: unknown) {
                alert(err instanceof Error ? err.message : "Xóa chủ đề thất bại.");
            }
        }
    };

    // Handle Material Upload
    const handleUploadSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!uploadFile) {
            setUploadError("Vui lòng chọn tệp tài liệu cần tải lên.");
            return;
        }

        setUploadError(null);
        setIsUploading(true);
        setUploadProgress(10);

        try {
            await materialService.uploadMaterial(
                uploadFile,
                id,
                uploadTopicId,
                (percent) => setUploadProgress(percent)
            );

            setIsUploadModalOpen(false);
            setUploadFile(null);
            setUploadTopicId(undefined);
            setUploadProgress(0);

            // Refresh materials list
            const updatedMaterials = await materialService.getMaterials(id);
            setMaterials(updatedMaterials);
        } catch (err: unknown) {
            setUploadError(err instanceof Error ? err.message : "Tải lên tài liệu thất bại.");
        } finally {
            setIsUploading(false);
        }
    };

    // Handle Material Delete
    const handleDeleteMaterial = async (matId: number, matName: string) => {
        if (window.confirm(`Bạn có chắc muốn xóa tài liệu "${matName}" không?`)) {
            try {
                await materialService.deleteMaterial(matId);
                setMaterials((prev) => prev.filter((m) => m.id !== matId));
            } catch (err: unknown) {
                alert(err instanceof Error ? err.message : "Xóa tài liệu thất bại.");
            }
        }
    };

    // Handle Material Download
    const handleDownload = async (matId: number, originalName: string) => {
        try {
            await materialService.downloadMaterial(matId, originalName);
        } catch (err: unknown) {
            alert(err instanceof Error ? err.message : "Tải tệp thất bại.");
        }
    };

    // Handle Quiz Generation
    const handleGenerateQuiz = async (e: React.FormEvent) => {
        e.preventDefault();
        setQuizError(null);

        if (!quizTitle.trim()) {
            setQuizError("Vui lòng nhập tiêu đề bài kiểm tra.");
            return;
        }

        try {
            setIsQuizGenerating(true);
            const created = await quizService.generateQuiz({
                courseId: id,
                materialId: quizMaterialId,
                title: quizTitle.trim(),
                difficulty: quizDifficulty,
                questionCount: quizQuestionCount
            });

            setIsQuizModalOpen(false);
            setQuizTitle("");
            setQuizzes((prev) => [created, ...prev]);
            navigate(`/quizzes/${created.id}/take`);
        } catch (err: unknown) {
            setQuizError(err instanceof Error ? err.message : "Không thể tạo đề thi AI.");
        } finally {
            setIsQuizGenerating(false);
        }
    };

    const filteredMaterials = selectedTopicId
        ? materials.filter((m) => m.topicId === selectedTopicId)
        : materials;

    if (isLoading) {
        return <LoadingSpinner size="lg" text="Đang tải dữ liệu khóa học..." className="py-20" />;
    }

    if (error || !course) {
        return (
            <div className="p-6 rounded-2xl bg-rose-50 border border-rose-200 text-rose-700">
                <p className="font-semibold">Lỗi</p>
                <p className="text-sm mt-1">{error || "Khóa học không tồn tại."}</p>
                <Link to="/courses" className="mt-4 inline-block text-sm font-semibold underline">
                    Quay lại danh sách khóa học
                </Link>
            </div>
        );
    }

    return (
        <div className="space-y-8">
            {/* Navigation back and header */}
            <div>
                <button
                    onClick={() => navigate("/courses")}
                    className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-indigo-600 transition mb-3"
                >
                    <ArrowLeft className="w-4 h-4" />
                    <span>Quay lại danh sách khóa học</span>
                </button>

                <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4">
                    <div>
                        <div className="flex items-center gap-2">
                            <span className="px-2.5 py-0.5 rounded-full bg-indigo-50 text-indigo-700 font-semibold text-xs border border-indigo-100">
                                Khóa học
                            </span>
                        </div>
                        <h1 className="text-2xl font-bold text-slate-800 mt-1">{course.title}</h1>
                        <p className="text-sm text-slate-500 mt-1">
                            {course.description || "Chưa có mô tả cho khóa học này."}
                        </p>
                    </div>

                    <div className="flex items-center gap-3">
                        <button
                            onClick={() => setIsUploadModalOpen(true)}
                            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-sm shadow-sm transition"
                        >
                            <UploadCloud className="w-4 h-4" />
                            <span>Tải tài liệu</span>
                        </button>
                        <button
                            onClick={() => {
                                setQuizTitle(`Đề thi: ${course.title}`);
                                setQuizError(null);
                                setIsQuizModalOpen(true);
                            }}
                            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white font-semibold text-sm shadow-sm transition"
                        >
                            <Sparkles className="w-4 h-4" />
                            <span>Tạo đề thi AI</span>
                        </button>
                    </div>
                </div>
            </div>

            {/* Layout: Left Sidebar Topics & Right Content Materials/Quizzes */}
            <div className="grid grid-cols-1 lg:grid-cols-4 gap-8">
                {/* Topics Column */}
                <div className="lg:col-span-1 space-y-4">
                    <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
                        <div className="flex items-center justify-between mb-4">
                            <h3 className="font-bold text-slate-800 text-sm flex items-center gap-2">
                                <Layers className="w-4 h-4 text-indigo-600" />
                                <span>Chủ đề / Chương ({course.topics.length})</span>
                            </h3>
                            <button
                                onClick={() => setIsTopicModalOpen(true)}
                                className="p-1 rounded-lg text-indigo-600 hover:bg-indigo-50"
                                title="Thêm chủ đề mới"
                            >
                                <Plus className="w-4 h-4" />
                            </button>
                        </div>

                        <div className="space-y-1">
                            <button
                                onClick={() => setSelectedTopicId(undefined)}
                                className={`w-full text-left px-3 py-2 rounded-xl text-xs font-medium transition flex items-center justify-between ${
                                    selectedTopicId === undefined
                                        ? "bg-indigo-50 text-indigo-700 font-semibold"
                                        : "text-slate-600 hover:bg-slate-50"
                                }`}
                            >
                                <span>Tất cả tài liệu</span>
                                <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-slate-100 text-slate-500">
                                    {materials.length}
                                </span>
                            </button>

                            {course.topics.map((t: Topic) => {
                                const count = materials.filter((m) => m.topicId === t.id).length;
                                return (
                                    <div
                                        key={t.id}
                                        className={`group flex items-center justify-between px-3 py-2 rounded-xl text-xs transition cursor-pointer ${
                                            selectedTopicId === t.id
                                                ? "bg-indigo-50 text-indigo-700 font-semibold"
                                                : "text-slate-600 hover:bg-slate-50"
                                        }`}
                                        onClick={() => setSelectedTopicId(t.id)}
                                    >
                                        <span className="line-clamp-1 flex-1 pr-2">{t.title}</span>
                                        <div className="flex items-center gap-1.5 shrink-0">
                                            <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-slate-100 text-slate-500">
                                                {count}
                                            </span>
                                            <button
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    handleDeleteTopic(t.id, t.title);
                                                }}
                                                className="opacity-0 group-hover:opacity-100 text-slate-400 hover:text-rose-600 p-0.5"
                                                title="Xóa chủ đề"
                                            >
                                                <Trash2 className="w-3.5 h-3.5" />
                                            </button>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    </div>
                </div>

                {/* Materials & Quizzes Main Column */}
                <div className="lg:col-span-3 space-y-8">
                    {/* Materials Section */}
                    <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
                        <div className="flex items-center justify-between mb-5">
                            <div>
                                <h3 className="font-bold text-slate-800 text-base flex items-center gap-2">
                                    <FileText className="w-5 h-5 text-indigo-600" />
                                    <span>Tài liệu học tập</span>
                                </h3>
                                <p className="text-xs text-slate-500 mt-0.5">
                                    Tài liệu PDF, Word, Text được AI tự động trích xuất nội dung
                                </p>
                            </div>
                            <button
                                onClick={() => setIsUploadModalOpen(true)}
                                className="text-xs font-semibold text-indigo-600 hover:text-indigo-700 flex items-center gap-1"
                            >
                                <Plus className="w-4 h-4" />
                                <span>Thêm tài liệu</span>
                            </button>
                        </div>

                        {filteredMaterials.length > 0 ? (
                            <div className="divide-y divide-slate-100">
                                {filteredMaterials.map((mat) => {
                                    const isSuccess = mat.extractionStatus === "SUCCESS";
                                    const isFailed = mat.extractionStatus === "FAILED";

                                    return (
                                        <div
                                            key={mat.id}
                                            className="py-3.5 flex flex-col sm:flex-row sm:items-center justify-between gap-3 hover:bg-slate-50/70 p-2 rounded-xl transition"
                                        >
                                            <div className="flex items-start gap-3 min-w-0">
                                                <div className="w-9 h-9 rounded-xl bg-slate-100 text-slate-600 flex items-center justify-center shrink-0">
                                                    <FileText className="w-5 h-5" />
                                                </div>
                                                <div className="min-w-0">
                                                    <h4 className="text-sm font-semibold text-slate-800 truncate">
                                                        {mat.originalName}
                                                    </h4>
                                                    <div className="flex items-center gap-2 text-[11px] text-slate-400 mt-0.5">
                                                        <span>{(mat.fileSize / 1024).toFixed(1)} KB</span>
                                                        <span>•</span>
                                                        {isSuccess && (
                                                            <span className="flex items-center gap-1 text-emerald-600 font-medium">
                                                                <CheckCircle2 className="w-3.5 h-3.5" /> Trích xuất hoàn tất
                                                            </span>
                                                        )}
                                                        {isFailed && (
                                                            <span className="flex items-center gap-1 text-rose-600 font-medium">
                                                                <AlertCircle className="w-3.5 h-3.5" /> Lỗi trích xuất (Scan/rỗng)
                                                            </span>
                                                        )}
                                                        {!isSuccess && !isFailed && (
                                                            <span className="flex items-center gap-1 text-amber-600 font-medium">
                                                                <Clock className="w-3.5 h-3.5" /> Đang xử lý
                                                            </span>
                                                        )}
                                                    </div>
                                                </div>
                                            </div>

                                            <div className="flex items-center gap-2 self-end sm:self-auto shrink-0">
                                                {isSuccess && (
                                                    <Link
                                                        to={`/materials/${mat.id}`}
                                                        className="px-3 py-1.5 rounded-lg bg-indigo-50 hover:bg-indigo-100 text-indigo-700 font-semibold text-xs flex items-center gap-1.5 transition"
                                                    >
                                                        <Sparkles className="w-3.5 h-3.5" />
                                                        <span>Học với AI</span>
                                                    </Link>
                                                )}
                                                <button
                                                    onClick={() => handleDownload(mat.id, mat.originalName)}
                                                    className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100"
                                                    title="Tải tệp gốc"
                                                >
                                                    <Download className="w-4 h-4" />
                                                </button>
                                                <button
                                                    onClick={() => handleDeleteMaterial(mat.id, mat.originalName)}
                                                    className="p-1.5 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50"
                                                    title="Xóa tài liệu"
                                                >
                                                    <Trash2 className="w-4 h-4" />
                                                </button>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        ) : (
                            <div className="text-center py-10 border border-dashed border-slate-200 rounded-xl">
                                <FileText className="w-8 h-8 text-slate-300 mx-auto mb-2" />
                                <p className="text-xs text-slate-500">Chưa có tài liệu nào trong phần này.</p>
                                <button
                                    onClick={() => setIsUploadModalOpen(true)}
                                    className="mt-2 text-xs font-semibold text-indigo-600 hover:underline"
                                >
                                    Tải tài liệu đầu tiên
                                </button>
                            </div>
                        )}
                    </div>

                    {/* AI Quizzes Section */}
                    <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
                        <div className="flex items-center justify-between mb-5">
                            <div>
                                <h3 className="font-bold text-slate-800 text-base flex items-center gap-2">
                                    <Award className="w-5 h-5 text-emerald-600" />
                                    <span>Đề thi trắc nghiệm AI ({quizzes.length})</span>
                                </h3>
                                <p className="text-xs text-slate-500 mt-0.5">
                                    Luyện tập kiểm tra kiến thức và đánh giá năng lực tự động
                                </p>
                            </div>
                            <button
                                onClick={() => {
                                    setQuizTitle(`Đề thi: ${course.title}`);
                                    setQuizError(null);
                                    setIsQuizModalOpen(true);
                                }}
                                className="text-xs font-semibold text-emerald-600 hover:text-emerald-700 flex items-center gap-1"
                            >
                                <Plus className="w-4 h-4" />
                                <span>Tạo đề mới</span>
                            </button>
                        </div>

                        {quizzes.length > 0 ? (
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                {quizzes.map((quiz) => (
                                    <div
                                        key={quiz.id}
                                        className="p-4 rounded-xl border border-slate-200 hover:border-emerald-300 bg-slate-50/50 hover:bg-white transition flex flex-col justify-between"
                                    >
                                        <div>
                                            <div className="flex items-center justify-between mb-2">
                                                <span className="text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800">
                                                    Độ khó: {quiz.difficulty}
                                                </span>
                                                <span className="text-xs text-slate-400">
                                                    {quiz.questionCount} câu hỏi
                                                </span>
                                            </div>
                                            <h4 className="text-sm font-bold text-slate-800 line-clamp-1">
                                                {quiz.title}
                                            </h4>
                                        </div>

                                        <div className="mt-4 pt-3 border-t border-slate-100 flex items-center justify-between">
                                            <Link
                                                to={`/quizzes/${quiz.id}/take`}
                                                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white font-semibold text-xs shadow-sm transition"
                                            >
                                                <Play className="w-3.5 h-3.5" />
                                                <span>Làm bài ngay</span>
                                            </Link>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        ) : (
                            <div className="text-center py-10 border border-dashed border-slate-200 rounded-xl">
                                <Award className="w-8 h-8 text-slate-300 mx-auto mb-2" />
                                <p className="text-xs text-slate-500">Chưa có bài thi trắc nghiệm nào.</p>
                                <button
                                    onClick={() => {
                                        setQuizTitle(`Đề thi: ${course.title}`);
                                        setQuizError(null);
                                        setIsQuizModalOpen(true);
                                    }}
                                    className="mt-2 text-xs font-semibold text-emerald-600 hover:underline"
                                >
                                    Tạo bài thi AI đầu tiên
                                </button>
                            </div>
                        )}
                    </div>
                </div>
            </div>

            {/* Create Topic Modal */}
            {isTopicModalOpen && (
                <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-sm flex items-center justify-center p-4">
                    <div className="bg-white rounded-2xl shadow-xl w-full max-w-sm overflow-hidden border border-slate-200">
                        <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
                            <h3 className="font-bold text-slate-800 text-sm">Thêm chủ đề mới</h3>
                            <button
                                onClick={() => setIsTopicModalOpen(false)}
                                className="text-slate-400 hover:text-slate-600 p-1"
                            >
                                <X className="w-4 h-4" />
                            </button>
                        </div>
                        <form onSubmit={handleCreateTopic} className="p-5 space-y-4">
                            <div>
                                <label className="block text-xs font-semibold text-slate-700 mb-1.5 uppercase">
                                    Tên chủ đề / Chương
                                </label>
                                <input
                                    type="text"
                                    value={topicTitle}
                                    onChange={(e) => setTopicTitle(e.target.value)}
                                    placeholder="Ví dụ: Chương 1: Giới thiệu..."
                                    required
                                    className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                                />
                            </div>
                            <div className="flex justify-end gap-2 pt-2">
                                <button
                                    type="button"
                                    onClick={() => setIsTopicModalOpen(false)}
                                    className="px-3.5 py-1.5 rounded-xl border border-slate-200 text-xs font-medium text-slate-600"
                                >
                                    Hủy
                                </button>
                                <button
                                    type="submit"
                                    disabled={isTopicSubmitting}
                                    className="px-4 py-1.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold transition"
                                >
                                    {isTopicSubmitting ? "Đang thêm..." : "Thêm chủ đề"}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Upload Material Modal */}
            {isUploadModalOpen && (
                <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-sm flex items-center justify-center p-4">
                    <div className="bg-white rounded-2xl shadow-xl w-full max-w-md overflow-hidden border border-slate-200">
                        <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between">
                            <h3 className="font-bold text-slate-800 text-base">Tải lên tài liệu học tập</h3>
                            <button
                                onClick={() => !isUploading && setIsUploadModalOpen(false)}
                                className="text-slate-400 hover:text-slate-600 p-1"
                            >
                                <X className="w-5 h-5" />
                            </button>
                        </div>

                        <form onSubmit={handleUploadSubmit} className="p-6 space-y-4">
                            {uploadError && (
                                <div className="p-3.5 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-start gap-2">
                                    <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                                    <span>{uploadError}</span>
                                </div>
                            )}

                            <div>
                                <label className="block text-xs font-semibold text-slate-700 mb-1.5 uppercase">
                                    Chọn tệp (.pdf, .docx, .txt - Tối đa 20MB)
                                </label>
                                <input
                                    type="file"
                                    accept=".pdf,.docx,.txt"
                                    onChange={(e) => setUploadFile(e.target.files ? e.target.files[0] : null)}
                                    disabled={isUploading}
                                    className="w-full text-xs text-slate-500 file:mr-4 file:py-2 file:px-4 file:rounded-xl file:border-0 file:text-xs file:font-semibold file:bg-indigo-50 file:text-indigo-700 hover:file:bg-indigo-100 cursor-pointer"
                                />
                            </div>

                            <div>
                                <label className="block text-xs font-semibold text-slate-700 mb-1.5 uppercase">
                                    Gắn vào chủ đề (Tùy chọn)
                                </label>
                                <select
                                    value={uploadTopicId || ""}
                                    onChange={(e) =>
                                        setUploadTopicId(e.target.value ? Number(e.target.value) : undefined)
                                    }
                                    disabled={isUploading}
                                    className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                                >
                                    <option value="">-- Thuộc toàn bộ khóa học --</option>
                                    {course.topics.map((t: Topic) => (
                                        <option key={t.id} value={t.id}>
                                            {t.title}
                                        </option>
                                    ))}
                                </select>
                            </div>

                            {/* Progress bar */}
                            {isUploading && (
                                <div className="space-y-1.5 pt-2">
                                    <div className="flex justify-between text-xs text-slate-500 font-medium">
                                        <span>Đang tải lên & trích xuất văn bản...</span>
                                        <span>{uploadProgress}%</span>
                                    </div>
                                    <div className="w-full h-2 bg-slate-100 rounded-full overflow-hidden">
                                        <div
                                            className="h-full bg-indigo-600 transition-all duration-300"
                                            style={{ width: `${uploadProgress}%` }}
                                        />
                                    </div>
                                </div>
                            )}

                            <div className="flex justify-end gap-3 pt-3">
                                <button
                                    type="button"
                                    disabled={isUploading}
                                    onClick={() => setIsUploadModalOpen(false)}
                                    className="px-4 py-2 rounded-xl border border-slate-200 text-sm font-medium text-slate-600"
                                >
                                    Hủy
                                </button>
                                <button
                                    type="submit"
                                    disabled={isUploading || !uploadFile}
                                    className="px-5 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold shadow-sm transition disabled:opacity-50"
                                >
                                    {isUploading ? "Đang xử lý..." : "Tải lên ngay"}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Generate Quiz Modal */}
            {isQuizModalOpen && (
                <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-sm flex items-center justify-center p-4">
                    <div className="bg-white rounded-2xl shadow-xl w-full max-w-md overflow-hidden border border-slate-200">
                        <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between">
                            <h3 className="font-bold text-slate-800 text-base flex items-center gap-2">
                                <Sparkles className="w-5 h-5 text-emerald-600" />
                                <span>Tạo đề trắc nghiệm bằng AI</span>
                            </h3>
                            <button
                                onClick={() => !isQuizGenerating && setIsQuizModalOpen(false)}
                                className="text-slate-400 hover:text-slate-600 p-1"
                            >
                                <X className="w-5 h-5" />
                            </button>
                        </div>

                        <form onSubmit={handleGenerateQuiz} className="p-6 space-y-4">
                            {quizError && (
                                <div className="p-3.5 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-start gap-2">
                                    <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                                    <span>{quizError}</span>
                                </div>
                            )}

                            <div>
                                <label className="block text-xs font-semibold text-slate-700 mb-1.5 uppercase">
                                    Tiêu đề bài kiểm tra
                                </label>
                                <input
                                    type="text"
                                    value={quizTitle}
                                    onChange={(e) => setQuizTitle(e.target.value)}
                                    placeholder="Ví dụ: Kiểm tra 15 phút Cơ sở dữ liệu"
                                    required
                                    disabled={isQuizGenerating}
                                    className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:ring-2 focus:ring-emerald-500 focus:outline-none"
                                />
                            </div>

                            <div>
                                <label className="block text-xs font-semibold text-slate-700 mb-1.5 uppercase">
                                    Chọn tài liệu nguồn (Tùy chọn)
                                </label>
                                <select
                                    value={quizMaterialId || ""}
                                    onChange={(e) =>
                                        setQuizMaterialId(e.target.value ? Number(e.target.value) : undefined)
                                    }
                                    disabled={isQuizGenerating}
                                    className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:ring-2 focus:ring-emerald-500 focus:outline-none"
                                >
                                    <option value="">-- Toàn bộ tài liệu trong khóa học --</option>
                                    {materials
                                        .filter((m) => m.extractionStatus === "SUCCESS")
                                        .map((m) => (
                                            <option key={m.id} value={m.id}>
                                                {m.originalName}
                                            </option>
                                        ))}
                                </select>
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-xs font-semibold text-slate-700 mb-1.5 uppercase">
                                        Mức độ khó
                                    </label>
                                    <select
                                        value={quizDifficulty}
                                        onChange={(e) => setQuizDifficulty(e.target.value as Difficulty)}
                                        disabled={isQuizGenerating}
                                        className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:ring-2 focus:ring-emerald-500 focus:outline-none"
                                    >
                                        <option value="EASY">Dễ (Nhận biết)</option>
                                        <option value="MEDIUM">Trung bình (Thông hiểu)</option>
                                        <option value="HARD">Khó (Vận dụng cao)</option>
                                    </select>
                                </div>

                                <div>
                                    <label className="block text-xs font-semibold text-slate-700 mb-1.5 uppercase">
                                        Số lượng câu hỏi
                                    </label>
                                    <input
                                        type="number"
                                        min={3}
                                        max={20}
                                        value={quizQuestionCount}
                                        onChange={(e) => setQuizQuestionCount(Number(e.target.value))}
                                        disabled={isQuizGenerating}
                                        className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:ring-2 focus:ring-emerald-500 focus:outline-none"
                                    />
                                </div>
                            </div>

                            <div className="flex justify-end gap-3 pt-3">
                                <button
                                    type="button"
                                    disabled={isQuizGenerating}
                                    onClick={() => setIsQuizModalOpen(false)}
                                    className="px-4 py-2 rounded-xl border border-slate-200 text-sm font-medium text-slate-600"
                                >
                                    Hủy
                                </button>
                                <button
                                    type="submit"
                                    disabled={isQuizGenerating}
                                    className="px-5 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-sm font-semibold shadow-sm transition disabled:opacity-50 flex items-center gap-2"
                                >
                                    {isQuizGenerating ? (
                                        <>
                                            <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                                            <span>AI đang tạo đề...</span>
                                        </>
                                    ) : (
                                        <>
                                            <Sparkles className="w-4 h-4" />
                                            <span>Bắt đầu sinh đề</span>
                                        </>
                                    )}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default CourseDetailPage;
