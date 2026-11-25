import React, { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
    AlertCircle,
    ArrowLeft,
    BookOpen,
    CheckCircle2,
    ChevronDown,
    ChevronRight,
    Clock,
    Download,
    Edit2,
    FileText,
    FolderPlus,
    Layers,
    Play,
    Plus,
    RefreshCw,
    Sparkles,
    Trash2,
    UploadCloud,
    X
} from "lucide-react";
import learningService from "../services/learningService";
import quizService from "../services/quizService";
import {
    Chapter,
    CourseDetail,
    Material,
    MaterialCategory,
    QuizDifficulty,
    QuizSummary,
    Topic
} from "../types";
import { useAiJob } from "../hooks/useAiJob";
import { Button } from "../components/ui/button";
import { Input } from "../components/ui/input";
import { Card, CardContent } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "../components/ui/table";
import { EmptyState } from "../components/common/EmptyState";
import { LoadingState } from "../components/common/LoadingState";
import { ErrorBanner } from "../components/common/ErrorBanner";
import { toast } from "sonner";

export const CourseDetailPage: React.FC = () => {
    const { courseId } = useParams<{ courseId: string }>();
    const navigate = useNavigate();
    const id = Number(courseId);

    const [course, setCourse] = useState<CourseDetail | null>(null);
    const [materials, setMaterials] = useState<Material[]>([]);
    const [quizzes, setQuizzes] = useState<QuizSummary[]>([]);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    // Selected Chapter / Topic filter
    const [selectedChapterId, setSelectedChapterId] = useState<number | null>(null);
    const [selectedTopicId, setSelectedTopicId] = useState<number | null>(null);

    // Chapter Modal
    const [isChapterModalOpen, setIsChapterModalOpen] = useState<boolean>(false);
    const [editingChapter, setEditingChapter] = useState<Chapter | null>(null);
    const [chapterTitle, setChapterTitle] = useState<string>("");
    const [chapterPosition, setChapterPosition] = useState<number>(1);

    // Topic Modal
    const [isTopicModalOpen, setIsTopicModalOpen] = useState<boolean>(false);
    const [topicTargetChapterId, setTopicTargetChapterId] = useState<number | null>(null);
    const [editingTopic, setEditingTopic] = useState<Topic | null>(null);
    const [topicTitle, setTopicTitle] = useState<string>("");
    const [topicPosition, setTopicPosition] = useState<number>(1);

    // Upload Material Modal
    const [isUploadModalOpen, setIsUploadModalOpen] = useState<boolean>(false);
    const [uploadFile, setUploadFile] = useState<File | null>(null);
    const [uploadTopicId, setUploadTopicId] = useState<number | undefined>(undefined);
    const [uploadCategory, setUploadCategory] = useState<MaterialCategory>("SLIDE");
    const [uploadProgress, setUploadProgress] = useState<number>(0);
    const [isUploading, setIsUploading] = useState<boolean>(false);
    const [uploadError, setUploadError] = useState<string | null>(null);

    // AI Quiz Generation Modal
    const [isQuizModalOpen, setIsQuizModalOpen] = useState<boolean>(false);
    const [quizTitle, setQuizTitle] = useState<string>("");
    const [quizQuestionCount, setQuizQuestionCount] = useState<number>(10);
    const [quizDifficulty, setQuizDifficulty] = useState<QuizDifficulty>("MEDIUM");
    const [quizSelectedMaterialIds, setQuizSelectedMaterialIds] = useState<number[]>([]);
    const [quizTopic, setQuizTopic] = useState<string>("");

    // AI Job Hook for Quiz Generation
    const {
        jobId: quizJobId,
        isLoading: isQuizJobLoading,
        progressText: quizProgressText,
        startJob: startQuizJob,
        cancel: cancelQuizJob
    } = useAiJob(null, {
        onCompleted: () => {
            toast.success("Tạo bài kiểm tra AI thành công!");
            setIsQuizModalOpen(false);
            void fetchQuizzes();
        },
        onError: (errMsg) => {
            toast.error(errMsg);
        }
    });

    const fetchAllData = async () => {
        try {
            setIsLoading(true);
            const [courseData, materialsData, quizzesData] = await Promise.all([
                learningService.getCourseDetail(id),
                learningService.getMaterials(id),
                quizService.listQuizzes(id)
            ]);
            setCourse(courseData);
            setMaterials(materialsData.items);
            setQuizzes(quizzesData.items);
            setError(null);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "Không thể tải chi tiết khóa học.");
        } finally {
            setIsLoading(false);
        }
    };

    const fetchQuizzes = async () => {
        try {
            const res = await quizService.listQuizzes(id);
            setQuizzes(res.items);
        } catch {
            // Ignore background refresh errors
        }
    };

    const fetchMaterials = async () => {
        try {
            const res = await learningService.getMaterials(id);
            setMaterials(res.items);
        } catch {
            // Ignore background refresh errors
        }
    };

    useEffect(() => {
        if (!isNaN(id)) {
            void fetchAllData();
        }
    }, [id]);

    // Auto-poll materials status every 3s if any is EXTRACTING
    useEffect(() => {
        const hasExtracting = materials.some((m) => m.status === "EXTRACTING");
        if (!hasExtracting) return;

        const interval = setInterval(() => {
            void fetchMaterials();
        }, 3000);

        return () => clearInterval(interval);
    }, [materials]);

    // -------------------------------------------------------------
    // Chapter Handlers
    // -------------------------------------------------------------
    const handleOpenCreateChapter = () => {
        setEditingChapter(null);
        setChapterTitle("");
        setChapterPosition((course?.chapters?.length ?? 0) + 1);
        setIsChapterModalOpen(true);
    };

    const handleOpenEditChapter = (chapter: Chapter, e: React.MouseEvent) => {
        e.stopPropagation();
        setEditingChapter(chapter);
        setChapterTitle(chapter.title);
        setChapterPosition(chapter.position);
        setIsChapterModalOpen(true);
    };

    const handleSaveChapter = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!chapterTitle.trim()) return;

        try {
            if (editingChapter) {
                const updated = await learningService.updateChapter(editingChapter.id, {
                    title: chapterTitle.trim(),
                    position: chapterPosition
                });
                setCourse((prev) =>
                    prev
                        ? {
                              ...prev,
                              chapters: prev.chapters.map((ch) =>
                                  ch.id === updated.id ? { ...ch, ...updated } : ch
                              )
                          }
                        : null
                );
                toast.success("Cập nhật chương thành công!");
            } else {
                const created = await learningService.createChapter(id, {
                    title: chapterTitle.trim(),
                    position: chapterPosition
                });
                setCourse((prev) =>
                    prev ? { ...prev, chapters: [...prev.chapters, created] } : null
                );
                toast.success("Thêm chương mới thành công!");
            }
            setIsChapterModalOpen(false);
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Thao tác chương thất bại.");
        }
    };

    const handleDeleteChapter = async (chapter: Chapter, e: React.MouseEvent) => {
        e.stopPropagation();
        if (!window.confirm(`Bạn có chắc muốn xóa chương "${chapter.title}" không?`)) return;

        try {
            await learningService.deleteChapter(chapter.id);
            setCourse((prev) =>
                prev ? { ...prev, chapters: prev.chapters.filter((ch) => ch.id !== chapter.id) } : null
            );
            if (selectedChapterId === chapter.id) {
                setSelectedChapterId(null);
                setSelectedTopicId(null);
            }
            toast.success("Đã xóa chương.");
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Xóa chương thất bại.");
        }
    };

    // -------------------------------------------------------------
    // Topic Handlers
    // -------------------------------------------------------------
    const handleOpenCreateTopic = (chapterId: number, e: React.MouseEvent) => {
        e.stopPropagation();
        setTopicTargetChapterId(chapterId);
        setEditingTopic(null);
        setTopicTitle("");
        const chapter = course?.chapters.find((c) => c.id === chapterId);
        setTopicPosition((chapter?.topics?.length ?? 0) + 1);
        setIsTopicModalOpen(true);
    };

    const handleOpenEditTopic = (topic: Topic, chapterId: number, e: React.MouseEvent) => {
        e.stopPropagation();
        setTopicTargetChapterId(chapterId);
        setEditingTopic(topic);
        setTopicTitle(topic.title);
        setTopicPosition(topic.position);
        setIsTopicModalOpen(true);
    };

    const handleSaveTopic = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!topicTitle.trim() || !topicTargetChapterId) return;

        try {
            if (editingTopic) {
                const updated = await learningService.updateTopic(editingTopic.id, {
                    title: topicTitle.trim(),
                    position: topicPosition
                });
                setCourse((prev) =>
                    prev
                        ? {
                              ...prev,
                              chapters: prev.chapters.map((ch) =>
                                  ch.id === topicTargetChapterId
                                      ? {
                                            ...ch,
                                            topics: ch.topics?.map((tp) =>
                                                tp.id === updated.id ? { ...tp, ...updated } : tp
                                            )
                                        }
                                      : ch
                              )
                          }
                        : null
                );
                toast.success("Cập nhật chủ đề thành công!");
            } else {
                const created = await learningService.createTopic(topicTargetChapterId, {
                    title: topicTitle.trim(),
                    position: topicPosition
                });
                setCourse((prev) =>
                    prev
                        ? {
                              ...prev,
                              chapters: prev.chapters.map((ch) =>
                                  ch.id === topicTargetChapterId
                                      ? { ...ch, topics: [...(ch.topics || []), created] }
                                      : ch
                              )
                          }
                        : null
                );
                toast.success("Thêm chủ đề thành công!");
            }
            setIsTopicModalOpen(false);
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Thao tác chủ đề thất bại.");
        }
    };

    const handleDeleteTopic = async (chapterId: number, topic: Topic, e: React.MouseEvent) => {
        e.stopPropagation();
        if (!window.confirm(`Bạn có chắc muốn xóa chủ đề "${topic.title}" không?`)) return;

        try {
            await learningService.deleteTopic(topic.id);
            setCourse((prev) =>
                prev
                    ? {
                          ...prev,
                          chapters: prev.chapters.map((ch) =>
                              ch.id === chapterId
                                  ? { ...ch, topics: ch.topics?.filter((tp) => tp.id !== topic.id) }
                                  : ch
                          )
                      }
                    : null
            );
            if (selectedTopicId === topic.id) {
                setSelectedTopicId(null);
            }
            toast.success("Đã xóa chủ đề.");
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Xóa chủ đề thất bại.");
        }
    };

    // -------------------------------------------------------------
    // Material Upload & Actions
    // -------------------------------------------------------------
    const handleUploadSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setUploadError(null);

        if (!uploadFile) {
            setUploadError("Vui lòng chọn một tệp tài liệu.");
            return;
        }

        const allowedExts = [".pdf", ".docx", ".txt"];
        const fileExt = uploadFile.name.substring(uploadFile.name.lastIndexOf(".")).toLowerCase();
        if (!allowedExts.includes(fileExt)) {
            setUploadError("Định dạng tệp không được hỗ trợ. Vui lòng chọn .pdf, .docx hoặc .txt.");
            return;
        }

        if (uploadFile.size > 20 * 1024 * 1024) {
            setUploadError("Dung lượng tệp vượt quá 20MB cho phép.");
            return;
        }

        try {
            setIsUploading(true);
            setUploadProgress(0);
            const created = await learningService.uploadMaterial(
                id,
                uploadFile,
                uploadTopicId,
                uploadCategory,
                (percent) => setUploadProgress(percent)
            );
            setMaterials((prev) => [created, ...prev]);
            toast.success(`Tải lên tài liệu "${created.fileName}" thành công!`);
            setIsUploadModalOpen(false);
            setUploadFile(null);
        } catch (err: unknown) {
            setUploadError(err instanceof Error ? err.message : "Tải lên tài liệu thất bại.");
        } finally {
            setIsUploading(false);
        }
    };

    const handleReextractMaterial = async (materialId: number) => {
        try {
            await learningService.reextractMaterial(materialId);
            setMaterials((prev) =>
                prev.map((m) => (m.id === materialId ? { ...m, status: "EXTRACTING" } : m))
            );
            toast.info("Đã yêu cầu trích xuất lại văn bản.");
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Không thể kích hoạt trích xuất lại.");
        }
    };

    const handleDeleteMaterial = async (material: Material) => {
        if (!window.confirm(`Bạn có chắc muốn xóa tài liệu "${material.fileName}" không?`)) return;

        try {
            await learningService.deleteMaterial(material.id);
            setMaterials((prev) => prev.filter((m) => m.id !== material.id));
            toast.success("Đã xóa tài liệu.");
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Xóa tài liệu thất bại.");
        }
    };

    // -------------------------------------------------------------
    // AI Quiz Generation
    // -------------------------------------------------------------
    const handleOpenGenerateQuiz = () => {
        setQuizTitle(`Quiz ${course?.name || "Môn học"}`);
        setQuizDifficulty("MEDIUM");
        setQuizQuestionCount(10);
        const extractedMatIds = materials
            .filter((m) => m.status === "EXTRACTED")
            .map((m) => m.id);
        setQuizSelectedMaterialIds(extractedMatIds);
        setIsQuizModalOpen(true);
    };

    const handleStartGenerateQuiz = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!quizTitle.trim()) {
            toast.error("Vui lòng nhập tiêu đề bài Quiz.");
            return;
        }
        if (quizSelectedMaterialIds.length === 0) {
            toast.error("Vui lòng chọn ít nhất 1 tài liệu đã trích xuất.");
            return;
        }

        try {
            const accepted = await quizService.generateQuiz({
                courseId: id,
                materialIds: quizSelectedMaterialIds,
                title: quizTitle.trim(),
                topic: quizTopic.trim() || undefined,
                difficulty: quizDifficulty,
                questionCount: quizQuestionCount
            });
            startQuizJob(accepted.jobId);
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Yêu cầu tạo quiz thất bại.");
        }
    };

    // Filter materials by selected chapter / topic
    const allTopics = course?.chapters.flatMap((c) => c.topics || []) || [];
    const filteredMaterials = materials.filter((m) => {
        if (selectedTopicId) {
            return m.topicId === selectedTopicId;
        }
        if (selectedChapterId) {
            const chapter = course?.chapters.find((c) => c.id === selectedChapterId);
            const chapterTopicIds = (chapter?.topics || []).map((t) => t.id);
            return m.topicId ? chapterTopicIds.includes(m.topicId) : false;
        }
        return true;
    });

    if (isLoading) {
        return <LoadingState text="Đang tải dữ liệu môn học..." />;
    }

    if (error || !course) {
        return (
            <ErrorBanner
                title="Không thể tải khóa học"
                message={error || "Khóa học không tồn tại."}
                onRetry={fetchAllData}
            />
        );
    }

    return (
        <div className="space-y-8">
            {/* Top Navigation & Course Header */}
            <div>
                <button
                    onClick={() => navigate("/courses")}
                    className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-primary transition mb-3"
                >
                    <ArrowLeft className="w-4 h-4" />
                    <span>Quay lại danh sách khóa học</span>
                </button>
                <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
                    <div className="space-y-1">
                        <div className="flex items-center gap-2.5">
                            <h1 className="text-2xl font-bold text-slate-900">{course.name}</h1>
                            <Badge variant="outline" className="text-xs">
                                Phiên bản #{course.version}
                            </Badge>
                        </div>
                        <p className="text-sm text-slate-500 max-w-2xl">
                            {course.description || "Chưa có mô tả cho khóa học này."}
                        </p>
                    </div>
                    <div className="flex items-center gap-2.5">
                        <Button
                            variant="secondary"
                            onClick={handleOpenGenerateQuiz}
                            className="gap-2"
                        >
                            <Sparkles className="w-4 h-4" />
                            <span>Tạo Quiz bằng AI</span>
                        </Button>
                        <Button
                            onClick={() => setIsUploadModalOpen(true)}
                            className="gap-2"
                        >
                            <UploadCloud className="w-4 h-4" />
                            <span>Tải tài liệu</span>
                        </Button>
                    </div>
                </div>
            </div>

            {/* Main Content Layout: Sidebar Tree + Content */}
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
                {/* Left 4 Cols: Chapter & Topic Tree */}
                <div className="lg:col-span-4 space-y-4">
                    <Card>
                        <CardContent className="p-4 space-y-3">
                            <div className="flex items-center justify-between pb-2 border-b border-slate-100">
                                <h3 className="font-semibold text-slate-900 text-sm flex items-center gap-2">
                                    <Layers className="w-4 h-4 text-primary" />
                                    <span>Chương & Chủ đề</span>
                                </h3>
                                <Button
                                    variant="ghost"
                                    size="sm"
                                    onClick={handleOpenCreateChapter}
                                    className="h-7 px-2 text-xs text-primary gap-1"
                                >
                                    <Plus className="w-3.5 h-3.5" />
                                    <span>Thêm chương</span>
                                </Button>
                            </div>

                            {/* View All Option */}
                            <button
                                onClick={() => {
                                    setSelectedChapterId(null);
                                    setSelectedTopicId(null);
                                }}
                                className={`w-full text-left px-3 py-2 rounded-lg text-xs font-semibold flex items-center justify-between transition ${
                                    selectedChapterId === null && selectedTopicId === null
                                        ? "bg-primary text-white"
                                        : "text-slate-700 hover:bg-slate-100"
                                }`}
                            >
                                <span>Tất cả tài liệu môn học</span>
                                <span className="opacity-80">({materials.length})</span>
                            </button>

                            {/* Chapters List */}
                            <div className="space-y-2">
                                {course.chapters.length === 0 ? (
                                    <p className="text-xs text-slate-400 py-3 text-center">
                                        Chưa có chương nào. Bấm "Thêm chương" để cấu trúc bài học.
                                    </p>
                                ) : (
                                    course.chapters
                                        .slice()
                                        .sort((a, b) => a.position - b.position)
                                        .map((chapter) => {
                                            const isSelectedChapter = selectedChapterId === chapter.id;

                                            return (
                                                <div
                                                    key={chapter.id}
                                                    className="rounded-lg border border-slate-200 overflow-hidden bg-white"
                                                >
                                                    {/* Chapter Header */}
                                                    <div
                                                        onClick={() => {
                                                            setSelectedChapterId(chapter.id);
                                                            setSelectedTopicId(null);
                                                        }}
                                                        className={`p-2.5 flex items-center justify-between gap-2 cursor-pointer transition ${
                                                            isSelectedChapter
                                                                ? "bg-blue-50/70 text-primary font-semibold"
                                                                : "text-slate-800 hover:bg-slate-50"
                                                        }`}
                                                    >
                                                        <div className="flex items-center gap-2 min-w-0">
                                                            <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-slate-100 text-slate-600 shrink-0">
                                                                #{chapter.position}
                                                            </span>
                                                            <span className="text-xs truncate font-medium">
                                                                {chapter.title}
                                                            </span>
                                                        </div>
                                                        <div className="flex items-center gap-1 shrink-0">
                                                            <Button
                                                                variant="ghost"
                                                                size="sm"
                                                                onClick={(e) => handleOpenCreateTopic(chapter.id, e)}
                                                                className="h-6 w-6 p-0 text-slate-400 hover:text-primary"
                                                                title="Thêm chủ đề con"
                                                            >
                                                                <Plus className="w-3 h-3" />
                                                            </Button>
                                                            <Button
                                                                variant="ghost"
                                                                size="sm"
                                                                onClick={(e) => handleOpenEditChapter(chapter, e)}
                                                                className="h-6 w-6 p-0 text-slate-400 hover:text-slate-700"
                                                                title="Sửa chương"
                                                            >
                                                                <Edit2 className="w-3 h-3" />
                                                            </Button>
                                                            <Button
                                                                variant="ghost"
                                                                size="sm"
                                                                onClick={(e) => handleDeleteChapter(chapter, e)}
                                                                className="h-6 w-6 p-0 text-slate-400 hover:text-rose-600"
                                                                title="Xóa chương"
                                                            >
                                                                <Trash2 className="w-3 h-3" />
                                                            </Button>
                                                        </div>
                                                    </div>

                                                    {/* Sub Topics List */}
                                                    {chapter.topics && chapter.topics.length > 0 && (
                                                        <div className="border-t border-slate-100 bg-slate-50/50 p-1.5 space-y-1">
                                                            {chapter.topics
                                                                .slice()
                                                                .sort((a, b) => a.position - b.position)
                                                                .map((topic) => {
                                                                    const isSelectedTopic = selectedTopicId === topic.id;

                                                                    return (
                                                                        <div
                                                                            key={topic.id}
                                                                            onClick={() => {
                                                                                setSelectedChapterId(chapter.id);
                                                                                setSelectedTopicId(topic.id);
                                                                            }}
                                                                            className={`px-2.5 py-1.5 rounded-md flex items-center justify-between gap-2 text-xs cursor-pointer transition ${
                                                                                isSelectedTopic
                                                                                    ? "bg-primary text-white font-medium"
                                                                                    : "text-slate-600 hover:bg-slate-200/60"
                                                                            }`}
                                                                        >
                                                                            <span className="truncate pl-2 border-l-2 border-slate-300">
                                                                                {topic.title}
                                                                            </span>
                                                                            <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100">
                                                                                <button
                                                                                    onClick={(e) => handleOpenEditTopic(topic, chapter.id, e)}
                                                                                    className="p-0.5 hover:text-slate-900"
                                                                                >
                                                                                    <Edit2 className="w-2.5 h-2.5" />
                                                                                </button>
                                                                                <button
                                                                                    onClick={(e) => handleDeleteTopic(chapter.id, topic, e)}
                                                                                    className="p-0.5 hover:text-rose-500"
                                                                                >
                                                                                    <Trash2 className="w-2.5 h-2.5" />
                                                                                </button>
                                                                            </div>
                                                                        </div>
                                                                    );
                                                                })}
                                                        </div>
                                                    )}
                                                </div>
                                            );
                                        })
                                )}
                            </div>
                        </CardContent>
                    </Card>

                    {/* AI Quizzes Card */}
                    <Card>
                        <CardContent className="p-4 space-y-3">
                            <div className="flex items-center justify-between pb-2 border-b border-slate-100">
                                <h3 className="font-semibold text-slate-900 text-sm flex items-center gap-2">
                                    <Sparkles className="w-4 h-4 text-amber-500" />
                                    <span>Bộ Quiz môn học</span>
                                </h3>
                                <Button
                                    variant="ghost"
                                    size="sm"
                                    onClick={handleOpenGenerateQuiz}
                                    className="h-7 px-2 text-xs text-primary gap-1"
                                >
                                    <Plus className="w-3.5 h-3.5" />
                                    <span>Tạo mới</span>
                                </Button>
                            </div>

                            {quizzes.length === 0 ? (
                                <p className="text-xs text-slate-400 py-3 text-center">
                                    Chưa có bài quiz nào. Nhấn "Tạo Quiz bằng AI" để tự động sinh câu hỏi trắc nghiệm.
                                </p>
                            ) : (
                                <div className="space-y-2">
                                    {quizzes.map((quiz) => (
                                        <div
                                            key={quiz.id}
                                            className="p-2.5 rounded-lg border border-slate-200 bg-white hover:border-primary/40 transition flex items-center justify-between gap-2"
                                        >
                                            <div className="min-w-0">
                                                <p className="text-xs font-semibold text-slate-900 truncate">
                                                    {quiz.title}
                                                </p>
                                                <div className="flex items-center gap-2 mt-1">
                                                    <Badge
                                                        variant={
                                                            quiz.difficulty === "EASY"
                                                                ? "success"
                                                                : quiz.difficulty === "HARD"
                                                                ? "danger"
                                                                : "warning"
                                                        }
                                                        className="text-[10px] py-0 px-1.5"
                                                    >
                                                        {quiz.difficulty === "EASY"
                                                            ? "Dễ"
                                                            : quiz.difficulty === "HARD"
                                                            ? "Khó"
                                                            : "Vừa"}
                                                    </Badge>
                                                    <span className="text-[11px] text-slate-500">
                                                        {quiz.questionCount} câu
                                                    </span>
                                                </div>
                                            </div>
                                            <Button
                                                size="sm"
                                                variant="outline"
                                                onClick={() => navigate(`/quizzes/${quiz.id}/take`)}
                                                className="gap-1 h-8 text-xs shrink-0"
                                            >
                                                <Play className="w-3 h-3 text-emerald-600 fill-emerald-600" />
                                                <span>Làm bài</span>
                                            </Button>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </CardContent>
                    </Card>
                </div>

                {/* Right 8 Cols: Materials Management Table */}
                <div className="lg:col-span-8 space-y-4">
                    <Card>
                        <CardContent className="p-6">
                            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4 pb-3 border-b border-slate-100">
                                <div>
                                    <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                                        <FileText className="w-5 h-5 text-primary" />
                                        <span>
                                            {selectedTopicId
                                                ? `Tài liệu: ${allTopics.find((t) => t.id === selectedTopicId)?.title}`
                                                : selectedChapterId
                                                ? `Tài liệu trong chương`
                                                : "Toàn bộ tài liệu môn học"}
                                        </span>
                                    </h2>
                                    <p className="text-xs text-slate-500 mt-0.5">
                                        Danh sách tài liệu đã trích xuất, sẵn sàng cho tóm tắt và hỏi đáp AI
                                    </p>
                                </div>
                                <Button
                                    size="sm"
                                    onClick={() => setIsUploadModalOpen(true)}
                                    className="gap-2 shrink-0"
                                >
                                    <UploadCloud className="w-4 h-4" />
                                    <span>Tải tài liệu mới</span>
                                </Button>
                            </div>

                            {filteredMaterials.length === 0 ? (
                                <EmptyState
                                    icon={<FileText className="w-8 h-8 text-slate-400" />}
                                    title="Chưa có tài liệu nào"
                                    description="Tải lên tài liệu dạng PDF, DOCX hoặc TXT để AI trích xuất nội dung và hỗ trợ học tập."
                                    action={
                                        <Button
                                            size="sm"
                                            onClick={() => setIsUploadModalOpen(true)}
                                            className="gap-2"
                                        >
                                            <UploadCloud className="w-4 h-4" />
                                            <span>Tải tài liệu ngay</span>
                                        </Button>
                                    }
                                />
                            ) : (
                                <div className="border border-slate-200 rounded-xl overflow-hidden">
                                    <Table>
                                        <TableHeader>
                                            <TableRow>
                                                <TableHead>Tên tài liệu</TableHead>
                                                <TableHead>Phân loại</TableHead>
                                                <TableHead>Trạng thái</TableHead>
                                                <TableHead>Dung lượng</TableHead>
                                                <TableHead className="text-right">Thao tác</TableHead>
                                            </TableRow>
                                        </TableHeader>
                                        <TableBody>
                                            {filteredMaterials.map((mat) => {
                                                const formatBytes = (bytes: number) => {
                                                    if (bytes < 1024) return `${bytes} B`;
                                                    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
                                                    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
                                                };

                                                const categoryName: Record<string, string> = {
                                                    SLIDE: "Bài giảng",
                                                    LECTURE_NOTE: "Ghi chú",
                                                    TEXTBOOK: "Giáo trình",
                                                    EXERCISE: "Bài tập",
                                                    OTHER: "Khác"
                                                };

                                                return (
                                                    <TableRow key={mat.id} className="hover:bg-slate-50/80">
                                                        <TableCell className="font-medium text-slate-900 max-w-xs truncate">
                                                            <div className="flex items-center gap-2">
                                                                <FileText className="w-4 h-4 text-slate-400 shrink-0" />
                                                                <span title={mat.fileName}>{mat.fileName}</span>
                                                            </div>
                                                        </TableCell>
                                                        <TableCell>
                                                            <span className="text-xs text-slate-600">
                                                                {categoryName[mat.category] || mat.category}
                                                            </span>
                                                        </TableCell>
                                                        <TableCell>
                                                            {mat.status === "EXTRACTED" ? (
                                                                <Badge variant="success" className="gap-1 text-[11px]">
                                                                    <CheckCircle2 className="w-3 h-3" />
                                                                    <span>Sẵn sàng</span>
                                                                </Badge>
                                                            ) : mat.status === "EXTRACTING" ? (
                                                                <Badge variant="warning" className="gap-1 text-[11px] animate-pulse">
                                                                    <RefreshCw className="w-3 h-3 animate-spin" />
                                                                    <span>Đang trích xuất</span>
                                                                </Badge>
                                                            ) : (
                                                                <div className="flex items-center gap-1.5">
                                                                    <Badge variant="danger" className="gap-1 text-[11px]">
                                                                        <AlertCircle className="w-3 h-3" />
                                                                        <span>Lỗi trích xuất</span>
                                                                    </Badge>
                                                                    <Button
                                                                        variant="ghost"
                                                                        size="sm"
                                                                        onClick={() => handleReextractMaterial(mat.id)}
                                                                        className="h-6 w-6 p-0 text-slate-500 hover:text-primary"
                                                                        title="Thử lại"
                                                                    >
                                                                        <RefreshCw className="w-3 h-3" />
                                                                    </Button>
                                                                </div>
                                                            )}
                                                        </TableCell>
                                                        <TableCell className="text-xs text-slate-500">
                                                            {formatBytes(mat.sizeBytes)}
                                                        </TableCell>
                                                        <TableCell className="text-right">
                                                            <div className="flex items-center justify-end gap-1.5">
                                                                {mat.status === "EXTRACTED" && (
                                                                    <Button
                                                                        size="sm"
                                                                        onClick={() => navigate(`/materials/${mat.id}`)}
                                                                        className="h-8 gap-1.5 text-xs"
                                                                    >
                                                                        <Sparkles className="w-3.5 h-3.5" />
                                                                        <span>Học với AI</span>
                                                                    </Button>
                                                                )}
                                                                <Button
                                                                    variant="ghost"
                                                                    size="sm"
                                                                    onClick={() => learningService.downloadMaterial(mat.id, mat.fileName)}
                                                                    className="h-8 w-8 p-0 text-slate-500 hover:text-slate-900"
                                                                    title="Tải tệp gốc"
                                                                >
                                                                    <Download className="w-4 h-4" />
                                                                </Button>
                                                                <Button
                                                                    variant="ghost"
                                                                    size="sm"
                                                                    onClick={() => handleDeleteMaterial(mat)}
                                                                    className="h-8 w-8 p-0 text-rose-500 hover:text-rose-700"
                                                                    title="Xóa tài liệu"
                                                                >
                                                                    <Trash2 className="w-4 h-4" />
                                                                </Button>
                                                            </div>
                                                        </TableCell>
                                                    </TableRow>
                                                );
                                            })}
                                        </TableBody>
                                    </Table>
                                </div>
                            )}
                        </CardContent>
                    </Card>
                </div>
            </div>

            {/* Modal: Chapter Create/Edit */}
            {isChapterModalOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4 animate-in fade-in">
                    <div className="w-full max-w-sm bg-white rounded-2xl shadow-elevated border border-slate-200 overflow-hidden">
                        <div className="p-5 border-b border-slate-100 flex items-center justify-between">
                            <h2 className="text-base font-bold text-slate-900">
                                {editingChapter ? "Cập nhật chương" : "Thêm chương mới"}
                            </h2>
                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => setIsChapterModalOpen(false)}
                                className="h-7 w-7 p-0"
                            >
                                <X className="w-4 h-4" />
                            </Button>
                        </div>
                        <form onSubmit={handleSaveChapter} className="p-5 space-y-4">
                            <div>
                                <label className="block text-xs font-semibold uppercase text-slate-600 mb-1.5">
                                    Tên chương <span className="text-rose-500">*</span>
                                </label>
                                <Input
                                    type="text"
                                    placeholder="Ví dụ: Chương 3: Tầng giao vận..."
                                    value={chapterTitle}
                                    onChange={(e) => setChapterTitle(e.target.value)}
                                    required
                                    autoFocus
                                />
                            </div>
                            <div>
                                <label className="block text-xs font-semibold uppercase text-slate-600 mb-1.5">
                                    Thứ tự hiển thị
                                </label>
                                <Input
                                    type="number"
                                    min={1}
                                    value={chapterPosition}
                                    onChange={(e) => setChapterPosition(Number(e.target.value))}
                                />
                            </div>
                            <div className="pt-2 flex items-center justify-end gap-2">
                                <Button
                                    type="button"
                                    variant="outline"
                                    onClick={() => setIsChapterModalOpen(false)}
                                >
                                    Hủy
                                </Button>
                                <Button type="submit">Lưu chương</Button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Modal: Topic Create/Edit */}
            {isTopicModalOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4 animate-in fade-in">
                    <div className="w-full max-w-sm bg-white rounded-2xl shadow-elevated border border-slate-200 overflow-hidden">
                        <div className="p-5 border-b border-slate-100 flex items-center justify-between">
                            <h2 className="text-base font-bold text-slate-900">
                                {editingTopic ? "Cập nhật chủ đề" : "Thêm chủ đề con"}
                            </h2>
                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => setIsTopicModalOpen(false)}
                                className="h-7 w-7 p-0"
                            >
                                <X className="w-4 h-4" />
                            </Button>
                        </div>
                        <form onSubmit={handleSaveTopic} className="p-5 space-y-4">
                            <div>
                                <label className="block text-xs font-semibold uppercase text-slate-600 mb-1.5">
                                    Tên chủ đề <span className="text-rose-500">*</span>
                                </label>
                                <Input
                                    type="text"
                                    placeholder="Ví dụ: Giao thức TCP và kiểm soát tắc nghẽn..."
                                    value={topicTitle}
                                    onChange={(e) => setTopicTitle(e.target.value)}
                                    required
                                    autoFocus
                                />
                            </div>
                            <div>
                                <label className="block text-xs font-semibold uppercase text-slate-600 mb-1.5">
                                    Thứ tự hiển thị
                                </label>
                                <Input
                                    type="number"
                                    min={1}
                                    value={topicPosition}
                                    onChange={(e) => setTopicPosition(Number(e.target.value))}
                                />
                            </div>
                            <div className="pt-2 flex items-center justify-end gap-2">
                                <Button
                                    type="button"
                                    variant="outline"
                                    onClick={() => setIsTopicModalOpen(false)}
                                >
                                    Hủy
                                </Button>
                                <Button type="submit">Lưu chủ đề</Button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Modal: Upload Material */}
            {isUploadModalOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4 animate-in fade-in">
                    <div className="w-full max-w-md bg-white rounded-2xl shadow-elevated border border-slate-200 overflow-hidden">
                        <div className="p-5 border-b border-slate-100 flex items-center justify-between">
                            <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                                <UploadCloud className="w-5 h-5 text-primary" />
                                <span>Tải lên tài liệu học tập</span>
                            </h2>
                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => !isUploading && setIsUploadModalOpen(false)}
                                disabled={isUploading}
                                className="h-7 w-7 p-0"
                            >
                                <X className="w-4 h-4" />
                            </Button>
                        </div>
                        <form onSubmit={handleUploadSubmit} className="p-6 space-y-4">
                            {uploadError && (
                                <div className="p-3 bg-rose-50 border border-rose-200 rounded-lg text-xs text-rose-700">
                                    {uploadError}
                                </div>
                            )}

                            <div>
                                <label className="block text-xs font-semibold uppercase text-slate-600 mb-1.5">
                                    Chọn tệp (.pdf, .docx, .txt tối đa 20MB) <span className="text-rose-500">*</span>
                                </label>
                                <Input
                                    type="file"
                                    accept=".pdf,.docx,.txt"
                                    onChange={(e) => setUploadFile(e.target.files?.[0] || null)}
                                    disabled={isUploading}
                                    required
                                />
                            </div>

                            <div>
                                <label className="block text-xs font-semibold uppercase text-slate-600 mb-1.5">
                                    Loại tài liệu
                                </label>
                                <select
                                    value={uploadCategory}
                                    onChange={(e) => setUploadCategory(e.target.value as MaterialCategory)}
                                    disabled={isUploading}
                                    className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-primary"
                                >
                                    <option value="SLIDE">Bài giảng (Slide)</option>
                                    <option value="LECTURE_NOTE">Ghi chú bài học (Lecture Note)</option>
                                    <option value="TEXTBOOK">Giáo trình / Sách (Textbook)</option>
                                    <option value="EXERCISE">Bài tập & Đáp án (Exercise)</option>
                                    <option value="OTHER">Tài liệu khác (Other)</option>
                                </select>
                            </div>

                            <div>
                                <label className="block text-xs font-semibold uppercase text-slate-600 mb-1.5">
                                    Gắn vào chủ đề (không bắt buộc)
                                </label>
                                <select
                                    value={uploadTopicId || ""}
                                    onChange={(e) => setUploadTopicId(e.target.value ? Number(e.target.value) : undefined)}
                                    disabled={isUploading}
                                    className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-primary"
                                >
                                    <option value="">-- Môn học chung (Chưa gắn chủ đề) --</option>
                                    {allTopics.map((tp) => (
                                        <option key={tp.id} value={tp.id}>
                                            {tp.title}
                                        </option>
                                    ))}
                                </select>
                            </div>

                            {isUploading && (
                                <div className="space-y-1.5 pt-2">
                                    <div className="flex justify-between text-xs text-slate-600">
                                        <span>Đang tải lên máy chủ...</span>
                                        <span>{uploadProgress}%</span>
                                    </div>
                                    <div className="w-full h-2 rounded-full bg-slate-100 overflow-hidden">
                                        <div
                                            className="h-full bg-primary transition-all duration-300"
                                            style={{ width: `${uploadProgress}%` }}
                                        />
                                    </div>
                                </div>
                            )}

                            <div className="pt-2 flex items-center justify-end gap-2">
                                <Button
                                    type="button"
                                    variant="outline"
                                    onClick={() => setIsUploadModalOpen(false)}
                                    disabled={isUploading}
                                >
                                    Hủy
                                </Button>
                                <Button type="submit" isLoading={isUploading}>
                                    Tải lên ngay
                                </Button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Modal: AI Quiz Generator */}
            {isQuizModalOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4 animate-in fade-in">
                    <div className="w-full max-w-lg bg-white rounded-2xl shadow-elevated border border-slate-200 overflow-hidden">
                        <div className="p-5 border-b border-slate-100 flex items-center justify-between">
                            <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                                <Sparkles className="w-5 h-5 text-amber-500" />
                                <span>Sinh bài kiểm tra trắc nghiệm bằng AI</span>
                            </h2>
                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => !isQuizJobLoading && setIsQuizModalOpen(false)}
                                disabled={isQuizJobLoading}
                                className="h-7 w-7 p-0"
                            >
                                <X className="w-4 h-4" />
                            </Button>
                        </div>
                        <form onSubmit={handleStartGenerateQuiz} className="p-6 space-y-4">
                            <div>
                                <label className="block text-xs font-semibold uppercase text-slate-600 mb-1.5">
                                    Tiêu đề bài Quiz <span className="text-rose-500">*</span>
                                </label>
                                <Input
                                    type="text"
                                    value={quizTitle}
                                    onChange={(e) => setQuizTitle(e.target.value)}
                                    required
                                    disabled={isQuizJobLoading}
                                />
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-xs font-semibold uppercase text-slate-600 mb-1.5">
                                        Số lượng câu hỏi
                                    </label>
                                    <select
                                        value={quizQuestionCount}
                                        onChange={(e) => setQuizQuestionCount(Number(e.target.value))}
                                        disabled={isQuizJobLoading}
                                        className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-primary"
                                    >
                                        <option value={5}>5 câu</option>
                                        <option value={10}>10 câu</option>
                                        <option value={15}>15 câu</option>
                                        <option value={20}>20 câu</option>
                                    </select>
                                </div>
                                <div>
                                    <label className="block text-xs font-semibold uppercase text-slate-600 mb-1.5">
                                        Mức độ khó
                                    </label>
                                    <select
                                        value={quizDifficulty}
                                        onChange={(e) => setQuizDifficulty(e.target.value as QuizDifficulty)}
                                        disabled={isQuizJobLoading}
                                        className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-primary"
                                    >
                                        <option value="EASY">Dễ (Khái niệm cơ bản)</option>
                                        <option value="MEDIUM">Vừa (Hiểu & vận dụng)</option>
                                        <option value="HARD">Khó (Phân tích chuyên sâu)</option>
                                    </select>
                                </div>
                            </div>

                            <div>
                                <label className="block text-xs font-semibold uppercase text-slate-600 mb-1.5">
                                    Chủ đề trọng tâm (tùy chọn)
                                </label>
                                <Input
                                    type="text"
                                    placeholder="Ví dụ: Kiểm soát tắc nghẽn, Sliding Window..."
                                    value={quizTopic}
                                    onChange={(e) => setQuizTopic(e.target.value)}
                                    disabled={isQuizJobLoading}
                                />
                            </div>

                            <div>
                                <label className="block text-xs font-semibold uppercase text-slate-600 mb-1.5">
                                    Chọn tài liệu làm nguồn câu hỏi <span className="text-rose-500">*</span>
                                </label>
                                <div className="max-h-36 overflow-y-auto border border-slate-200 rounded-lg p-2 space-y-1.5 bg-slate-50/50">
                                    {materials.filter((m) => m.status === "EXTRACTED").length === 0 ? (
                                        <p className="text-xs text-slate-400 p-2 text-center">
                                            Chưa có tài liệu nào ở trạng thái "Sẵn sàng" để sinh quiz.
                                        </p>
                                    ) : (
                                        materials
                                            .filter((m) => m.status === "EXTRACTED")
                                            .map((mat) => {
                                                const checked = quizSelectedMaterialIds.includes(mat.id);
                                                return (
                                                    <label
                                                        key={mat.id}
                                                        className="flex items-center gap-2 p-1.5 rounded hover:bg-slate-100 cursor-pointer text-xs text-slate-800"
                                                    >
                                                        <input
                                                            type="checkbox"
                                                            checked={checked}
                                                            disabled={isQuizJobLoading}
                                                            onChange={(e) => {
                                                                if (e.target.checked) {
                                                                    setQuizSelectedMaterialIds((prev) => [...prev, mat.id]);
                                                                } else {
                                                                    setQuizSelectedMaterialIds((prev) =>
                                                                        prev.filter((mid) => mid !== mat.id)
                                                                    );
                                                                }
                                                            }}
                                                            className="rounded border-slate-300 text-primary focus:ring-primary"
                                                        />
                                                        <span className="truncate">{mat.fileName}</span>
                                                    </label>
                                                );
                                            })
                                    )}
                                </div>
                            </div>

                            {isQuizJobLoading && (
                                <div className="p-4 rounded-xl bg-blue-50 border border-blue-100 flex items-center justify-between gap-3">
                                    <div className="flex items-center gap-2.5">
                                        <Sparkles className="w-4 h-4 text-primary animate-spin" />
                                        <span className="text-xs font-medium text-slate-800">
                                            {quizProgressText}
                                        </span>
                                    </div>
                                    <Button
                                        type="button"
                                        variant="outline"
                                        size="sm"
                                        onClick={cancelQuizJob}
                                        className="h-7 text-xs text-rose-600 border-rose-200 hover:bg-rose-50"
                                    >
                                        Hủy tác vụ
                                    </Button>
                                </div>
                            )}

                            <div className="pt-2 flex items-center justify-end gap-2">
                                <Button
                                    type="button"
                                    variant="outline"
                                    onClick={() => setIsQuizModalOpen(false)}
                                    disabled={isQuizJobLoading}
                                >
                                    Đóng
                                </Button>
                                <Button
                                    type="submit"
                                    isLoading={isQuizJobLoading}
                                    className="gap-2"
                                >
                                    <Sparkles className="w-4 h-4" />
                                    <span>Tạo Quiz ngay</span>
                                </Button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default CourseDetailPage;
