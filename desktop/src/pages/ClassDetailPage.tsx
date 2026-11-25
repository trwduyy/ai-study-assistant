import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
    Users,
    MessageSquare,
    FileText,
    ClipboardList,
    Clock,
    Plus,
    Pin,
    Download,
    Trash2,
    Edit3,
    Check,
    X,
    Lock,
    DoorOpen,
    Archive,
    UserPlus,
    UserMinus,
    LogOut,
    ArrowLeft,
    CheckCircle2,
    AlertCircle,
    Calendar,
    Award,
    Play,
    BarChart3,
    UploadCloud,
    Paperclip
} from "lucide-react";
import { toast } from "sonner";
import { classroomService } from "../services/classroomService";
import { assignmentService } from "../services/assignmentService";
import { testService } from "../services/testService";
import { quizService } from "../services/quizService";
import {
    Classroom,
    ClassRole,
    ClassroomStatus,
    ClassPost,
    ClassMaterial,
    AssignmentSummary,
    ClassTest,
    ClassMember,
    ClassJoinRequest,
    QuizSummary
} from "../types";
import { useAuth } from "../contexts/AuthContext";
import { Button } from "../components/ui/button";
import { Input } from "../components/ui/input";
import { Badge } from "../components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogFooter
} from "../components/ui/dialog";
import { LoadingState } from "../components/common/LoadingState";
import { ErrorBanner } from "../components/common/ErrorBanner";
import { EmptyState } from "../components/common/EmptyState";

type TabKey = "feed" | "materials" | "assignments" | "tests" | "members";

export const ClassDetailPage: React.FC = () => {
    const { classId } = useParams<{ classId: string }>();
    const numericClassId = Number(classId);
    const navigate = useNavigate();
    const { user } = useAuth();

    const [classroom, setClassroom] = useState<Classroom | null>(null);
    const [activeTab, setActiveTab] = useState<TabKey>("feed");
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    // Permissions
    const isOwner = classroom?.myClassRole === "OWNER_TEACHER";
    const isCoTeacher = classroom?.myClassRole === "CO_TEACHER";
    const isTeacher = isOwner || isCoTeacher || user?.role === "ADMIN";

    // -------------------------------------------------------------------------
    // TAB 1: Bảng tin (Feed)
    // -------------------------------------------------------------------------
    const [posts, setPosts] = useState<ClassPost[]>([]);
    const [isPostsLoading, setIsPostsLoading] = useState<boolean>(false);
    const [isCreatePostOpen, setIsCreatePostOpen] = useState<boolean>(false);
    const [postTitle, setPostTitle] = useState<string>("");
    const [postContent, setPostContent] = useState<string>("");
    const [postPinned, setPostPinned] = useState<boolean>(false);
    const [isPosting, setIsPosting] = useState<boolean>(false);

    // -------------------------------------------------------------------------
    // TAB 2: Tài liệu lớp (Materials)
    // -------------------------------------------------------------------------
    const [materials, setMaterials] = useState<ClassMaterial[]>([]);
    const [isMaterialsLoading, setIsMaterialsLoading] = useState<boolean>(false);
    const [isUploadOpen, setIsUploadOpen] = useState<boolean>(false);
    const [uploadFile, setUploadFile] = useState<File | null>(null);
    const [isUploading, setIsUploading] = useState<boolean>(false);

    // -------------------------------------------------------------------------
    // TAB 3: Bài tập (Assignments)
    // -------------------------------------------------------------------------
    const [assignments, setAssignments] = useState<AssignmentSummary[]>([]);
    const [isAssignmentsLoading, setIsAssignmentsLoading] = useState<boolean>(false);
    const [isCreateAssignmentOpen, setIsCreateAssignmentOpen] = useState<boolean>(false);
    const [assignTitle, setAssignTitle] = useState<string>("");
    const [assignInstructions, setAssignInstructions] = useState<string>("");
    const [assignMaxScore, setAssignMaxScore] = useState<number>(10);
    const [assignOpenAt, setAssignOpenAt] = useState<string>("");
    const [assignDueAt, setAssignDueAt] = useState<string>("");
    const [assignAllowLate, setAssignAllowLate] = useState<boolean>(true);
    const [assignFiles, setAssignFiles] = useState<File[]>([]);
    const [isSavingAssignment, setIsSavingAssignment] = useState<boolean>(false);

    // -------------------------------------------------------------------------
    // TAB 4: Bài kiểm tra (Tests)
    // -------------------------------------------------------------------------
    const [tests, setTests] = useState<ClassTest[]>([]);
    const [isTestsLoading, setIsTestsLoading] = useState<boolean>(false);
    const [isCreateTestOpen, setIsCreateTestOpen] = useState<boolean>(false);
    const [availableQuizzes, setAvailableQuizzes] = useState<QuizSummary[]>([]);
    const [selectedQuizId, setSelectedQuizId] = useState<number | "">("");
    const [testTitle, setTestTitle] = useState<string>("");
    const [testStartAt, setTestStartAt] = useState<string>("");
    const [testEndAt, setTestEndAt] = useState<string>("");
    const [testDuration, setTestDuration] = useState<number>(15);
    const [testMaxAttempts, setTestMaxAttempts] = useState<number>(1);
    const [testShuffle, setTestShuffle] = useState<boolean>(true);
    const [isSavingTest, setIsSavingTest] = useState<boolean>(false);

    // -------------------------------------------------------------------------
    // TAB 5: Thành viên & Yêu cầu chờ duyệt (Members)
    // -------------------------------------------------------------------------
    const [members, setMembers] = useState<ClassMember[]>([]);
    const [isMembersLoading, setIsMembersLoading] = useState<boolean>(false);
    const [joinRequests, setJoinRequests] = useState<ClassJoinRequest[]>([]);
    const [isAddCoTeacherOpen, setIsAddCoTeacherOpen] = useState<boolean>(false);
    const [coTeacherUserId, setCoTeacherUserId] = useState<string>("");
    const [isAddingCoTeacher, setIsAddingCoTeacher] = useState<boolean>(false);

    // Fetch initial class info
    const fetchClassDetails = async () => {
        if (!numericClassId) return;
        try {
            setIsLoading(true);
            const data = await classroomService.getClassroomDetail(numericClassId);
            setClassroom(data);
            setError(null);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "Không thể tải thông tin lớp học.");
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        void fetchClassDetails();
    }, [numericClassId]);

    // Tab-specific fetchers
    const fetchPosts = async () => {
        if (!numericClassId) return;
        try {
            setIsPostsLoading(true);
            const res = await classroomService.getPosts(numericClassId);
            setPosts(res.items);
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Lỗi tải bảng tin.");
        } finally {
            setIsPostsLoading(false);
        }
    };

    const fetchMaterials = async () => {
        if (!numericClassId) return;
        try {
            setIsMaterialsLoading(true);
            const res = await classroomService.getMaterials(numericClassId);
            setMaterials(res.items);
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Lỗi tải tài liệu lớp.");
        } finally {
            setIsMaterialsLoading(false);
        }
    };

    const fetchAssignments = async () => {
        if (!numericClassId) return;
        try {
            setIsAssignmentsLoading(true);
            const res = await assignmentService.getAssignments(numericClassId);
            setAssignments(res.items);
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Lỗi tải danh sách bài tập.");
        } finally {
            setIsAssignmentsLoading(false);
        }
    };

    const fetchTests = async () => {
        if (!numericClassId) return;
        try {
            setIsTestsLoading(true);
            const res = await testService.getTests(numericClassId);
            setTests(res.items);
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Lỗi tải bài kiểm tra.");
        } finally {
            setIsTestsLoading(false);
        }
    };

    const fetchMembersAndRequests = async () => {
        if (!numericClassId) return;
        try {
            setIsMembersLoading(true);
            const [memsRes, reqsRes] = await Promise.all([
                classroomService.getMembers(numericClassId),
                isTeacher ? classroomService.getJoinRequests(numericClassId, "PENDING") : Promise.resolve({ items: [] })
            ]);
            setMembers(memsRes.items);
            setJoinRequests(reqsRes.items);
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Lỗi tải thành viên lớp.");
        } finally {
            setIsMembersLoading(false);
        }
    };

    // Trigger tab fetch on tab change
    useEffect(() => {
        if (!classroom) return;
        if (activeTab === "feed") void fetchPosts();
        if (activeTab === "materials") void fetchMaterials();
        if (activeTab === "assignments") void fetchAssignments();
        if (activeTab === "tests") void fetchTests();
        if (activeTab === "members") void fetchMembersAndRequests();
    }, [activeTab, classroom]);

    // -------------------------------------------------------------------------
    // Handlers: Posts
    // -------------------------------------------------------------------------
    const handleCreatePost = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!postTitle.trim() || !postContent.trim()) {
            toast.error("Vui lòng nhập tiêu đề và nội dung thông báo.");
            return;
        }

        try {
            setIsPosting(true);
            await classroomService.createPost(numericClassId, {
                title: postTitle.trim(),
                content: postContent.trim(),
                pinned: postPinned
            });
            toast.success("Đã đăng thông báo lên bảng tin!");
            setIsCreatePostOpen(false);
            setPostTitle("");
            setPostContent("");
            setPostPinned(false);
            void fetchPosts();
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Đăng bài thất bại.");
        } finally {
            setIsPosting(false);
        }
    };

    const handleTogglePin = async (post: ClassPost) => {
        try {
            await classroomService.pinPost(numericClassId, post.id, {
                pinned: !post.pinned
            });
            toast.success(post.pinned ? "Đã bỏ ghim bài đăng." : "Đã ghim bài đăng lên đầu bảng tin.");
            void fetchPosts();
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Thao tác ghim thất bại.");
        }
    };

    // -------------------------------------------------------------------------
    // Handlers: Materials
    // -------------------------------------------------------------------------
    const handleUploadMaterial = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!uploadFile) {
            toast.error("Vui lòng chọn tệp tài liệu cần tải lên.");
            return;
        }

        if (uploadFile.size > 20 * 1024 * 1024) {
            toast.error("Dung lượng tệp vượt quá giới hạn 20MB.");
            return;
        }

        try {
            setIsUploading(true);
            await classroomService.uploadMaterial(numericClassId, uploadFile);
            toast.success("Tải tài liệu lên lớp học thành công!");
            setIsUploadOpen(false);
            setUploadFile(null);
            void fetchMaterials();
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Tải tài liệu thất bại.");
        } finally {
            setIsUploading(false);
        }
    };

    // -------------------------------------------------------------------------
    // Handlers: Assignments
    // -------------------------------------------------------------------------
    const handleCreateAssignment = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!assignTitle.trim() || !assignDueAt) {
            toast.error("Vui lòng nhập tiêu đề và hạn nộp bài.");
            return;
        }

        try {
            setIsSavingAssignment(true);
            const nowIso = new Date().toISOString();
            await assignmentService.createAssignment(
                numericClassId,
                {
                    title: assignTitle.trim(),
                    instructions: assignInstructions.trim(),
                    maxScore: Number(assignMaxScore) || 10,
                    openAt: assignOpenAt ? new Date(assignOpenAt).toISOString() : nowIso,
                    dueAt: new Date(assignDueAt).toISOString(),
                    allowLate: assignAllowLate,
                    maxSubmissions: 1
                },
                assignFiles
            );
            toast.success("Tạo bài tập thành công!");
            setIsCreateAssignmentOpen(false);
            setAssignTitle("");
            setAssignInstructions("");
            setAssignMaxScore(10);
            setAssignOpenAt("");
            setAssignDueAt("");
            setAssignFiles([]);
            void fetchAssignments();
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Tạo bài tập thất bại.");
        } finally {
            setIsSavingAssignment(false);
        }
    };

    const handleDeleteAssignment = async (assignment: AssignmentSummary, e: React.MouseEvent) => {
        e.stopPropagation();
        if (!window.confirm(`Bạn có chắc chắn muốn xóa bài tập "${assignment.title}" không?`)) return;

        try {
            await assignmentService.deleteAssignment(assignment.id);
            toast.success("Đã xóa bài tập.");
            void fetchAssignments();
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Xóa bài tập thất bại.");
        }
    };

    // -------------------------------------------------------------------------
    // Handlers: Tests
    // -------------------------------------------------------------------------
    const handleOpenCreateTest = async () => {
        setIsCreateTestOpen(true);
        try {
            const res = await quizService.listQuizzes(undefined, 0, 50);
            setAvailableQuizzes(res.items);
            if (res.items.length > 0) {
                setSelectedQuizId(res.items[0].id);
                setTestTitle(res.items[0].title);
            }
        } catch {
            toast.error("Không thể tải danh sách bộ câu hỏi Quiz.");
        }
    };

    const handleCreateTest = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!selectedQuizId || !testTitle.trim() || !testStartAt || !testEndAt) {
            toast.error("Vui lòng điền đầy đủ tiêu đề và khung giờ kiểm tra.");
            return;
        }

        try {
            setIsSavingTest(true);
            await testService.createTest(numericClassId, {
                quizId: Number(selectedQuizId),
                title: testTitle.trim(),
                startAt: new Date(testStartAt).toISOString(),
                endAt: new Date(testEndAt).toISOString(),
                durationMinutes: Number(testDuration) || 15,
                maxAttempts: Number(testMaxAttempts) || 1,
                shuffleQuestions: testShuffle
            });
            toast.success("Tạo bài kiểm tra thành công!");
            setIsCreateTestOpen(false);
            setTestTitle("");
            setTestStartAt("");
            setTestEndAt("");
            void fetchTests();
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Tạo bài kiểm tra thất bại.");
        } finally {
            setIsSavingTest(false);
        }
    };

    const handleDeleteTest = async (testId: number, title: string, e: React.MouseEvent) => {
        e.stopPropagation();
        if (!window.confirm(`Bạn có chắc muốn xóa bài kiểm tra "${title}" không?`)) return;

        try {
            await testService.deleteTest(testId);
            toast.success("Đã xóa bài kiểm tra.");
            void fetchTests();
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Xóa bài kiểm tra thất bại.");
        }
    };

    // -------------------------------------------------------------------------
    // Handlers: Members & Join Requests
    // -------------------------------------------------------------------------
    const handleDecideRequest = async (reqId: number, decision: "APPROVED" | "REJECTED") => {
        let reason: string | null = null;
        if (decision === "REJECTED") {
            const input = window.prompt("Nhập lý do từ chối (tùy chọn):");
            if (input === null) return;
            reason = input.trim() || null;
        }

        try {
            await classroomService.decideJoinRequest(numericClassId, reqId, {
                decision,
                rejectionReason: reason
            });
            toast.success(decision === "APPROVED" ? "Đã duyệt sinh viên vào lớp!" : "Đã từ chối yêu cầu.");
            void fetchMembersAndRequests();
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Thao tác xét duyệt thất bại.");
        }
    };

    const handleAddCoTeacher = async (e: React.FormEvent) => {
        e.preventDefault();
        const uid = Number(coTeacherUserId);
        if (!uid) {
            toast.error("Vui lòng nhập ID giảng viên hợp lệ.");
            return;
        }

        try {
            setIsAddingCoTeacher(true);
            await classroomService.addCoTeacher(numericClassId, { userId: uid });
            toast.success("Đã thêm giảng viên đồng giảng dạy!");
            setIsAddCoTeacherOpen(false);
            setCoTeacherUserId("");
            void fetchMembersAndRequests();
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Thêm đồng giảng dạy thất bại.");
        } finally {
            setIsAddingCoTeacher(false);
        }
    };

    const handleRemoveMember = async (member: ClassMember) => {
        if (!window.confirm(`Bạn có chắc chắn muốn gỡ thành viên "${member.fullName}" khỏi lớp không?`)) return;

        try {
            await classroomService.removeMember(numericClassId, member.userId);
            toast.success("Đã gỡ thành viên khỏi lớp.");
            void fetchMembersAndRequests();
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Gỡ thành viên thất bại.");
        }
    };

    const handleLeaveClass = async () => {
        if (!window.confirm("Bạn có chắc muốn rời khỏi lớp học này? Bạn sẽ mất quyền xem tài liệu, bài tập và phòng chat của lớp.")) {
            return;
        }

        try {
            await classroomService.leaveClassroom(numericClassId);
            toast.success("Bạn đã rời khỏi lớp học.");
            navigate("/classes");
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Rời lớp thất bại.");
        }
    };

    const handleChangeStatus = async (newStatus: ClassroomStatus) => {
        if (!classroom) return;
        try {
            const updated = await classroomService.updateStatus(classroom.id, {
                status: newStatus,
                version: classroom.version
            });
            setClassroom(updated);
            toast.success(`Đã đổi trạng thái lớp sang "${newStatus}".`);
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Đổi trạng thái lớp thất bại.");
        }
    };

    if (isLoading) {
        return <LoadingState label="Đang tải dữ liệu lớp học..." />;
    }

    if (error || !classroom) {
        return (
            <ErrorBanner
                title="Lỗi tải lớp học"
                message={error || "Không tìm thấy thông tin lớp học."}
                onRetry={fetchClassDetails}
            />
        );
    }

    return (
        <div className="space-y-6 p-6 max-w-7xl mx-auto">
            {/* Navigation & Header */}
            <div className="space-y-3 pb-4 border-b border-slate-200">
                <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => navigate("/classes")}
                    className="gap-1.5 text-xs text-slate-500 hover:text-slate-800 -ml-2 h-8"
                >
                    <ArrowLeft className="w-4 h-4" />
                    Quay lại danh sách lớp
                </Button>

                <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                    <div className="space-y-1">
                        <div className="flex items-center gap-2">
                            <span className="text-xs font-bold uppercase tracking-wider text-primary">
                                {classroom.subject}
                            </span>
                            <Badge
                                variant={classroom.status === "OPEN" ? "success" : classroom.status === "CLOSED" ? "warning" : "outline"}
                                className="text-xs"
                            >
                                {classroom.status === "OPEN" ? "Đang mở" : classroom.status === "CLOSED" ? "Đã đóng" : "Lưu trữ"}
                            </Badge>
                        </div>
                        <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
                            {classroom.name}
                        </h1>
                        <p className="text-xs text-slate-500">
                            Giảng viên chủ nhiệm: <strong className="text-slate-700">{classroom.ownerName}</strong> • Mã lớp:{" "}
                            <span className="font-mono font-bold text-slate-800 bg-slate-100 px-1.5 py-0.5 rounded">
                                {classroom.code}
                            </span>
                        </p>
                    </div>

                    {/* Teacher Class Actions / Student Leave */}
                    <div className="flex items-center gap-2">
                        {isOwner && (
                            <div className="flex items-center gap-1.5 bg-slate-50 border border-slate-200 p-1 rounded-lg text-xs">
                                <span className="text-slate-500 px-1">Trạng thái:</span>
                                <button
                                    onClick={() => handleChangeStatus("OPEN")}
                                    className={`px-2 py-1 rounded font-medium transition-colors ${
                                        classroom.status === "OPEN" ? "bg-emerald-600 text-white" : "text-slate-600 hover:text-slate-900"
                                    }`}
                                >
                                    Mở
                                </button>
                                <button
                                    onClick={() => handleChangeStatus("CLOSED")}
                                    className={`px-2 py-1 rounded font-medium transition-colors ${
                                        classroom.status === "CLOSED" ? "bg-amber-600 text-white" : "text-slate-600 hover:text-slate-900"
                                    }`}
                                >
                                    Đóng
                                </button>
                                <button
                                    onClick={() => handleChangeStatus("ARCHIVED")}
                                    className={`px-2 py-1 rounded font-medium transition-colors ${
                                        classroom.status === "ARCHIVED" ? "bg-slate-700 text-white" : "text-slate-600 hover:text-slate-900"
                                    }`}
                                >
                                    Lưu trữ
                                </button>
                            </div>
                        )}

                        {!isOwner && (
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={handleLeaveClass}
                                className="text-xs text-rose-600 hover:bg-rose-50 border-rose-200 gap-1.5 h-9"
                            >
                                <LogOut className="w-3.5 h-3.5" />
                                Rời lớp
                            </Button>
                        )}
                    </div>
                </div>
            </div>

            {/* 5 Tabs Header */}
            <div className="flex border-b border-slate-200 space-x-1 sm:space-x-4 overflow-x-auto text-sm font-semibold">
                <button
                    onClick={() => setActiveTab("feed")}
                    className={`py-3 px-3.5 border-b-2 flex items-center gap-2 transition-colors whitespace-nowrap ${
                        activeTab === "feed"
                            ? "border-primary text-primary"
                            : "border-transparent text-slate-500 hover:text-slate-800"
                    }`}
                >
                    <MessageSquare className="w-4 h-4" />
                    <span>Bảng tin</span>
                </button>
                <button
                    onClick={() => setActiveTab("materials")}
                    className={`py-3 px-3.5 border-b-2 flex items-center gap-2 transition-colors whitespace-nowrap ${
                        activeTab === "materials"
                            ? "border-primary text-primary"
                            : "border-transparent text-slate-500 hover:text-slate-800"
                    }`}
                >
                    <FileText className="w-4 h-4" />
                    <span>Tài liệu lớp</span>
                </button>
                <button
                    onClick={() => setActiveTab("assignments")}
                    className={`py-3 px-3.5 border-b-2 flex items-center gap-2 transition-colors whitespace-nowrap ${
                        activeTab === "assignments"
                            ? "border-primary text-primary"
                            : "border-transparent text-slate-500 hover:text-slate-800"
                    }`}
                >
                    <ClipboardList className="w-4 h-4" />
                    <span>Bài tập</span>
                </button>
                <button
                    onClick={() => setActiveTab("tests")}
                    className={`py-3 px-3.5 border-b-2 flex items-center gap-2 transition-colors whitespace-nowrap ${
                        activeTab === "tests"
                            ? "border-primary text-primary"
                            : "border-transparent text-slate-500 hover:text-slate-800"
                    }`}
                >
                    <Clock className="w-4 h-4" />
                    <span>Bài kiểm tra</span>
                </button>
                <button
                    onClick={() => setActiveTab("members")}
                    className={`py-3 px-3.5 border-b-2 flex items-center gap-2 transition-colors whitespace-nowrap ${
                        activeTab === "members"
                            ? "border-primary text-primary"
                            : "border-transparent text-slate-500 hover:text-slate-800"
                    }`}
                >
                    <Users className="w-4 h-4" />
                    <span>Thành viên</span>
                    {joinRequests.length > 0 && isTeacher && (
                        <span className="w-5 h-5 rounded-full bg-amber-500 text-white text-[10px] flex items-center justify-center font-bold">
                            {joinRequests.length}
                        </span>
                    )}
                </button>
            </div>

            {/* TAB CONTENT */}

            {/* 1. BẢNG TIN */}
            {activeTab === "feed" && (
                <div className="space-y-5">
                    {isTeacher && (
                        <div className="flex justify-end">
                            <Button
                                onClick={() => setIsCreatePostOpen(true)}
                                className="gap-2 text-xs"
                            >
                                <Plus className="w-4 h-4" />
                                Đăng thông báo mới
                            </Button>
                        </div>
                    )}

                    {isPostsLoading ? (
                        <LoadingState label="Đang tải thông báo..." />
                    ) : posts.length === 0 ? (
                        <EmptyState
                            icon={<MessageSquare className="w-10 h-10 text-slate-300" />}
                            title="Bảng tin chưa có bài đăng"
                            description="Giảng viên sẽ thông báo lịch học, dặn dò bài tập hoặc tài liệu tại đây."
                        />
                    ) : (
                        <div className="space-y-4">
                            {posts.map((post) => (
                                <Card
                                    key={post.id}
                                    className={`border-slate-200 shadow-sm ${
                                        post.pinned ? "border-l-4 border-l-amber-500 bg-amber-50/20" : ""
                                    }`}
                                >
                                    <CardContent className="p-5 space-y-3">
                                        <div className="flex items-start justify-between gap-3">
                                            <div className="space-y-1">
                                                <div className="flex items-center gap-2">
                                                    {post.pinned && (
                                                        <Badge variant="warning" className="gap-1 text-[11px] font-semibold">
                                                            <Pin className="w-3 h-3 fill-amber-600" />
                                                            Đã ghim
                                                        </Badge>
                                                    )}
                                                    <h3 className="font-bold text-base text-slate-900">
                                                        {post.title}
                                                    </h3>
                                                </div>
                                                <p className="text-xs text-slate-500">
                                                    Đăng bởi <strong>{post.authorName}</strong> •{" "}
                                                    {new Date(post.createdAt).toLocaleString("vi-VN", {
                                                        day: "2-digit",
                                                        month: "2-digit",
                                                        hour: "2-digit",
                                                        minute: "2-digit"
                                                    })}
                                                </p>
                                            </div>

                                            {isTeacher && (
                                                <Button
                                                    size="sm"
                                                    variant="ghost"
                                                    onClick={() => handleTogglePin(post)}
                                                    className="text-xs text-slate-500 hover:text-amber-600 gap-1 h-8"
                                                >
                                                    <Pin className={`w-3.5 h-3.5 ${post.pinned ? "fill-amber-600 text-amber-600" : ""}`} />
                                                    <span>{post.pinned ? "Bỏ ghim" : "Ghim"}</span>
                                                </Button>
                                            )}
                                        </div>

                                        <p className="text-sm text-slate-700 whitespace-pre-line leading-relaxed">
                                            {post.content}
                                        </p>
                                    </CardContent>
                                </Card>
                            ))}
                        </div>
                    )}
                </div>
            )}

            {/* 2. TÀI LIỆU LỚP */}
            {activeTab === "materials" && (
                <div className="space-y-5">
                    {isTeacher && (
                        <div className="flex justify-end">
                            <Button
                                onClick={() => setIsUploadOpen(true)}
                                className="gap-2 text-xs"
                            >
                                <UploadCloud className="w-4 h-4" />
                                Tải tài liệu lên lớp
                            </Button>
                        </div>
                    )}

                    {isMaterialsLoading ? (
                        <LoadingState label="Đang tải tài liệu..." />
                    ) : materials.length === 0 ? (
                        <EmptyState
                            icon={<FileText className="w-10 h-10 text-slate-300" />}
                            title="Chưa có tài liệu học tập"
                            description="Tài liệu bài giảng (.pdf, .docx, .txt) do giảng viên tải lên sẽ xuất hiện tại đây."
                        />
                    ) : (
                        <Card className="border-slate-200 shadow-sm overflow-hidden">
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm text-left">
                                    <thead className="bg-slate-50 text-slate-700 text-xs uppercase font-semibold border-b border-slate-200">
                                        <tr>
                                            <th className="py-3 px-4">Tên tệp</th>
                                            <th className="py-3 px-4">Định dạng</th>
                                            <th className="py-3 px-4">Dung lượng</th>
                                            <th className="py-3 px-4">Người tải</th>
                                            <th className="py-3 px-4">Ngày tải</th>
                                            <th className="py-3 px-4 text-right">Tải về</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-slate-100">
                                        {materials.map((mat) => (
                                            <tr key={mat.id} className="hover:bg-slate-50/80 transition-colors">
                                                <td className="py-3 px-4 font-semibold text-slate-900 flex items-center gap-2">
                                                    <Paperclip className="w-4 h-4 text-slate-400 shrink-0" />
                                                    <span className="truncate max-w-sm">{mat.fileName}</span>
                                                </td>
                                                <td className="py-3 px-4 text-xs text-slate-500">
                                                    {mat.mimeType || "Tệp đính kèm"}
                                                </td>
                                                <td className="py-3 px-4 text-xs text-slate-500">
                                                    {(mat.sizeBytes / (1024 * 1024)).toFixed(2)} MB
                                                </td>
                                                <td className="py-3 px-4 text-xs text-slate-600">
                                                    {mat.uploaderName}
                                                </td>
                                                <td className="py-3 px-4 text-xs text-slate-500">
                                                    {new Date(mat.createdAt).toLocaleDateString("vi-VN")}
                                                </td>
                                                <td className="py-3 px-4 text-right">
                                                    <a
                                                        href={classroomService.getDownloadMaterialUrl(mat.id)}
                                                        download
                                                        target="_blank"
                                                        rel="noopener noreferrer"
                                                        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold text-primary hover:bg-primary/10 transition-colors"
                                                    >
                                                        <Download className="w-3.5 h-3.5" />
                                                        Tải tệp
                                                    </a>
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        </Card>
                    )}
                </div>
            )}

            {/* 3. BÀI TẬP */}
            {activeTab === "assignments" && (
                <div className="space-y-5">
                    {isTeacher && (
                        <div className="flex justify-end">
                            <Button
                                onClick={() => setIsCreateAssignmentOpen(true)}
                                className="gap-2 text-xs"
                            >
                                <Plus className="w-4 h-4" />
                                Giao bài tập mới
                            </Button>
                        </div>
                    )}

                    {isAssignmentsLoading ? (
                        <LoadingState label="Đang tải danh sách bài tập..." />
                    ) : assignments.length === 0 ? (
                        <EmptyState
                            icon={<ClipboardList className="w-10 h-10 text-slate-300" />}
                            title="Lớp học chưa có bài tập nào"
                            description="Các bài tập về nhà và thực hành sẽ được liệt kê tại đây."
                        />
                    ) : (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            {assignments.map((assign) => {
                                const isOverdue = new Date(assign.dueAt) < new Date();

                                return (
                                    <Card
                                        key={assign.id}
                                        className="border-slate-200 shadow-sm hover:border-slate-300 transition-colors flex flex-col justify-between"
                                    >
                                        <CardContent className="p-5 space-y-4">
                                            <div className="flex items-start justify-between gap-3">
                                                <div className="space-y-1">
                                                    <h3 className="font-bold text-base text-slate-900 line-clamp-1">
                                                        {assign.title}
                                                    </h3>
                                                    <p className="text-xs text-slate-500">
                                                        Thang điểm tối đa: <strong>{assign.maxScore}</strong>
                                                    </p>
                                                </div>

                                                {/* Student Submission Badge */}
                                                {!isTeacher && assign.mySubmissionStatus && (
                                                    <Badge
                                                        variant={
                                                            assign.mySubmissionStatus === "GRADED" || assign.mySubmissionStatus === "RETURNED"
                                                                ? "success"
                                                                : "secondary"
                                                        }
                                                        className="shrink-0 text-xs"
                                                    >
                                                        {assign.mySubmissionStatus === "GRADED" || assign.mySubmissionStatus === "RETURNED"
                                                            ? "Đã chấm"
                                                            : assign.mySubmissionStatus === "SUBMITTED"
                                                            ? "Đã nộp"
                                                            : "Mở lại nộp"}
                                                    </Badge>
                                                )}
                                            </div>

                                            <div className="space-y-1 text-xs text-slate-600 bg-slate-50 p-2.5 rounded-lg border border-slate-100">
                                                <p className="flex items-center gap-1.5">
                                                    <Clock className="w-3.5 h-3.5 text-slate-400" />
                                                    <span>Hạn chót: <strong>{new Date(assign.dueAt).toLocaleString("vi-VN", {
                                                        day: "2-digit",
                                                        month: "2-digit",
                                                        hour: "2-digit",
                                                        minute: "2-digit"
                                                    })}</strong></span>
                                                    {isOverdue && (
                                                        <span className="text-rose-600 font-bold ml-1">(Đã hết hạn)</span>
                                                    )}
                                                </p>
                                                <p className="text-[11px] text-slate-500">
                                                    {assign.allowLate ? "Cho phép nộp muộn sau hạn" : "Khóa nộp bài khi hết hạn"}
                                                </p>
                                            </div>

                                            <div className="flex items-center justify-between pt-2 border-t border-slate-100">
                                                {isTeacher && (
                                                    <Button
                                                        size="sm"
                                                        variant="ghost"
                                                        onClick={(e) => handleDeleteAssignment(assign, e)}
                                                        className="text-xs text-rose-600 hover:bg-rose-50 h-8 px-2"
                                                    >
                                                        <Trash2 className="w-3.5 h-3.5 mr-1" />
                                                        Xóa
                                                    </Button>
                                                )}

                                                <Button
                                                    size="sm"
                                                    variant="default"
                                                    onClick={() => navigate(`/assignments/${assign.id}`)}
                                                    className="text-xs ml-auto h-8 bg-teal-700 hover:bg-teal-800"
                                                >
                                                    {isTeacher ? "Xem bài nộp & Chấm điểm" : "Chi tiết & Nộp bài"}
                                                </Button>
                                            </div>
                                        </CardContent>
                                    </Card>
                                );
                            })}
                        </div>
                    )}
                </div>
            )}

            {/* 4. BÀI KIỂM TRA */}
            {activeTab === "tests" && (
                <div className="space-y-5">
                    {isTeacher && (
                        <div className="flex justify-end">
                            <Button
                                onClick={handleOpenCreateTest}
                                className="gap-2 text-xs"
                            >
                                <Plus className="w-4 h-4" />
                                Tạo bài kiểm tra định thời
                            </Button>
                        </div>
                    )}

                    {isTestsLoading ? (
                        <LoadingState label="Đang tải bài kiểm tra..." />
                    ) : tests.length === 0 ? (
                        <EmptyState
                            icon={<Clock className="w-10 h-10 text-slate-300" />}
                            title="Chưa có bài kiểm tra"
                            description="Các bài kiểm tra trắc nghiệm định thời sẽ hiển thị tại đây khi giảng viên lên lịch."
                        />
                    ) : (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            {tests.map((test) => {
                                const now = new Date();
                                const start = new Date(test.startAt);
                                const end = new Date(test.endAt);
                                const isOpening = now >= start && now <= end;
                                const isUpcoming = now < start;
                                const isClosed = now > end;

                                return (
                                    <Card
                                        key={test.id}
                                        className="border-slate-200 shadow-sm hover:border-slate-300 transition-colors flex flex-col justify-between"
                                    >
                                        <CardContent className="p-5 space-y-4">
                                            <div className="flex items-start justify-between gap-3">
                                                <div className="space-y-1">
                                                    <h3 className="font-bold text-base text-slate-900">
                                                        {test.title}
                                                    </h3>
                                                    <p className="text-xs text-slate-500">
                                                        Thời lượng: <strong>{test.durationMinutes} phút</strong> • Tối đa:{" "}
                                                        <strong>{test.maxAttempts} lần làm</strong>
                                                    </p>
                                                </div>

                                                <Badge
                                                    variant={isOpening ? "success" : isUpcoming ? "warning" : "outline"}
                                                    className="text-xs font-semibold shrink-0"
                                                >
                                                    {isOpening ? "Đang diễn ra" : isUpcoming ? "Sắp mở" : "Đã kết thúc"}
                                                </Badge>
                                            </div>

                                            <div className="space-y-1 text-xs text-slate-600 bg-slate-50 p-2.5 rounded-lg border border-slate-100">
                                                <p>
                                                    Mở lúc: <strong>{start.toLocaleString("vi-VN", {
                                                        day: "2-digit",
                                                        month: "2-digit",
                                                        hour: "2-digit",
                                                        minute: "2-digit"
                                                    })}</strong>
                                                </p>
                                                <p>
                                                    Đóng lúc: <strong>{end.toLocaleString("vi-VN", {
                                                        day: "2-digit",
                                                        month: "2-digit",
                                                        hour: "2-digit",
                                                        minute: "2-digit"
                                                    })}</strong>
                                                </p>
                                            </div>

                                            <div className="flex items-center justify-between pt-2 border-t border-slate-100">
                                                {isTeacher ? (
                                                    <div className="flex items-center gap-2 w-full justify-between">
                                                        <Button
                                                            size="sm"
                                                            variant="ghost"
                                                            onClick={(e) => handleDeleteTest(test.id, test.title, e)}
                                                            className="text-xs text-rose-600 hover:bg-rose-50 h-8"
                                                        >
                                                            <Trash2 className="w-3.5 h-3.5 mr-1" />
                                                            Xóa
                                                        </Button>

                                                        <Button
                                                            size="sm"
                                                            variant="outline"
                                                            onClick={() => navigate(`/tests/${test.id}/results`)}
                                                            className="text-xs gap-1.5 h-8 text-primary border-primary/20"
                                                        >
                                                            <BarChart3 className="w-3.5 h-3.5" />
                                                            Xem bảng điểm lớp
                                                        </Button>
                                                    </div>
                                                ) : (
                                                    <Button
                                                        size="sm"
                                                        variant="default"
                                                        disabled={!isOpening}
                                                        onClick={() => navigate(`/tests/${test.id}/take`)}
                                                        className="w-full text-xs gap-1.5 h-8"
                                                    >
                                                        <Play className="w-3.5 h-3.5" />
                                                        {isOpening ? "Bắt đầu làm bài" : isUpcoming ? "Chưa đến giờ mở" : "Đã kết thúc"}
                                                    </Button>
                                                )}
                                            </div>
                                        </CardContent>
                                    </Card>
                                );
                            })}
                        </div>
                    )}
                </div>
            )}

            {/* 5. THÀNH VIÊN */}
            {activeTab === "members" && (
                <div className="space-y-6">
                    {/* Teacher: Pending Join Requests */}
                    {isTeacher && joinRequests.length > 0 && (
                        <div className="space-y-3 p-4 rounded-xl border border-amber-200 bg-amber-50/50">
                            <div className="flex items-center justify-between">
                                <h3 className="font-bold text-sm text-amber-900 flex items-center gap-2">
                                    <Clock className="w-4 h-4 text-amber-600" />
                                    Yêu cầu tham gia chờ duyệt ({joinRequests.length})
                                </h3>
                            </div>

                            <div className="divide-y divide-amber-200/60 bg-white rounded-lg border border-amber-200">
                                {joinRequests.map((req) => (
                                    <div
                                        key={req.id}
                                        className="p-3.5 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-sm"
                                    >
                                        <div>
                                            <p className="font-semibold text-slate-900">{req.studentName}</p>
                                            <p className="text-xs text-slate-500">{req.studentEmail} • Gửi lúc: {new Date(req.createdAt).toLocaleString("vi-VN")}</p>
                                        </div>
                                        <div className="flex items-center gap-2 self-end sm:self-auto">
                                            <Button
                                                size="sm"
                                                variant="outline"
                                                onClick={() => handleDecideRequest(req.id, "REJECTED")}
                                                className="text-xs text-rose-600 hover:bg-rose-50 h-8 gap-1"
                                            >
                                                <X className="w-3.5 h-3.5" />
                                                Từ chối
                                            </Button>
                                            <Button
                                                size="sm"
                                                variant="default"
                                                onClick={() => handleDecideRequest(req.id, "APPROVED")}
                                                className="text-xs bg-emerald-600 hover:bg-emerald-700 h-8 gap-1"
                                            >
                                                <Check className="w-3.5 h-3.5" />
                                                Duyệt vào lớp
                                            </Button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Members List */}
                    <div className="space-y-4">
                        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                            <h3 className="font-bold text-base text-slate-900 flex items-center gap-2">
                                <Users className="w-5 h-5 text-primary" />
                                Danh sách thành viên ({members.length})
                            </h3>

                            {isOwner && (
                                <Button
                                    size="sm"
                                    variant="outline"
                                    onClick={() => setIsAddCoTeacherOpen(true)}
                                    className="text-xs gap-1.5 h-8 text-teal-700 border-teal-300 hover:bg-teal-50"
                                >
                                    <UserPlus className="w-3.5 h-3.5" />
                                    Thêm đồng giảng dạy
                                </Button>
                            )}
                        </div>

                        {isMembersLoading ? (
                            <LoadingState label="Đang tải danh sách thành viên..." />
                        ) : members.length === 0 ? (
                            <EmptyState
                                icon={<Users className="w-10 h-10 text-slate-300" />}
                                title="Lớp học chưa có thành viên"
                                description="Chia sẻ mã lớp học để sinh viên gửi yêu cầu tham gia."
                            />
                        ) : (
                            <Card className="border-slate-200 shadow-sm overflow-hidden">
                                <div className="overflow-x-auto">
                                    <table className="w-full text-sm text-left">
                                        <thead className="bg-slate-50 text-slate-700 text-xs uppercase font-semibold border-b border-slate-200">
                                            <tr>
                                                <th className="py-3 px-4">Họ và tên</th>
                                                <th className="py-3 px-4">Email</th>
                                                <th className="py-3 px-4">Vai trò</th>
                                                <th className="py-3 px-4">Ngày tham gia</th>
                                                {isTeacher && <th className="py-3 px-4 text-right">Thao tác</th>}
                                            </tr>
                                        </thead>
                                        <tbody className="divide-y divide-slate-100">
                                            {members.map((mem) => {
                                                const isMemOwner = mem.classRole === "OWNER_TEACHER";
                                                const isMemCo = mem.classRole === "CO_TEACHER";

                                                return (
                                                    <tr key={mem.id} className="hover:bg-slate-50/80 transition-colors">
                                                        <td className="py-3 px-4 font-semibold text-slate-900">
                                                            {mem.fullName}
                                                        </td>
                                                        <td className="py-3 px-4 text-xs text-slate-500">
                                                            {mem.email}
                                                        </td>
                                                        <td className="py-3 px-4 text-xs">
                                                            <Badge
                                                                variant={isMemOwner ? "default" : isMemCo ? "secondary" : "outline"}
                                                                className="text-[11px]"
                                                            >
                                                                {isMemOwner
                                                                    ? "Giảng viên chủ nhiệm"
                                                                    : isMemCo
                                                                    ? "Đồng giảng dạy"
                                                                    : "Sinh viên"}
                                                            </Badge>
                                                        </td>
                                                        <td className="py-3 px-4 text-xs text-slate-500">
                                                            {new Date(mem.joinedAt).toLocaleDateString("vi-VN")}
                                                        </td>
                                                        {isTeacher && (
                                                            <td className="py-3 px-4 text-right">
                                                                {/* Only Owner can remove co-teachers; teacher cannot remove owner */}
                                                                {!isMemOwner && (isOwner || !isMemCo) && (
                                                                    <Button
                                                                        size="sm"
                                                                        variant="ghost"
                                                                        onClick={() => handleRemoveMember(mem)}
                                                                        className="text-xs text-rose-600 hover:bg-rose-50 h-7 px-2"
                                                                    >
                                                                        <UserMinus className="w-3.5 h-3.5 mr-1" />
                                                                        Gỡ
                                                                    </Button>
                                                                )}
                                                            </td>
                                                        )}
                                                    </tr>
                                                );
                                            })}
                                        </tbody>
                                    </table>
                                </div>
                            </Card>
                        )}
                    </div>
                </div>
            )}

            {/* MODALS */}

            {/* Create Post Modal */}
            <Dialog open={isCreatePostOpen} onOpenChange={setIsCreatePostOpen}>
                <DialogContent className="max-w-md">
                    <DialogHeader>
                        <DialogTitle className="text-lg font-bold text-slate-900">
                            Đăng thông báo mới lên bảng tin
                        </DialogTitle>
                    </DialogHeader>

                    <form onSubmit={handleCreatePost} className="space-y-4 py-2">
                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Tiêu đề thông báo <span className="text-rose-500">*</span>
                            </label>
                            <Input
                                value={postTitle}
                                onChange={(e) => setPostTitle(e.target.value)}
                                placeholder="Ví dụ: Lịch kiểm tra giữa kỳ, dặn dò..."
                                required
                                disabled={isPosting}
                            />
                        </div>

                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Nội dung chi tiết <span className="text-rose-500">*</span>
                            </label>
                            <textarea
                                value={postContent}
                                onChange={(e) => setPostContent(e.target.value)}
                                placeholder="Nhập nội dung dặn dò sinh viên..."
                                rows={4}
                                className="w-full text-sm rounded-lg border border-slate-200 p-2.5 focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary disabled:opacity-50"
                                required
                                disabled={isPosting}
                            />
                        </div>

                        <label className="flex items-center gap-2 text-xs font-medium text-slate-700 cursor-pointer pt-1">
                            <input
                                type="checkbox"
                                checked={postPinned}
                                onChange={(e) => setPostPinned(e.target.checked)}
                                className="rounded text-primary focus:ring-primary h-4 w-4"
                                disabled={isPosting}
                            />
                            <span>Ghim thông báo này lên đầu bảng tin</span>
                        </label>

                        <DialogFooter className="pt-2">
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => setIsCreatePostOpen(false)}
                                disabled={isPosting}
                            >
                                Hủy
                            </Button>
                            <Button
                                type="submit"
                                variant="default"
                                disabled={isPosting}
                            >
                                {isPosting ? "Đang đăng..." : "Đăng thông báo"}
                            </Button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            </Dialog>

            {/* Upload Material Modal */}
            <Dialog open={isUploadOpen} onOpenChange={setIsUploadOpen}>
                <DialogContent className="max-w-md">
                    <DialogHeader>
                        <DialogTitle className="text-lg font-bold text-slate-900">
                            Tải tài liệu lên lớp học
                        </DialogTitle>
                    </DialogHeader>

                    <form onSubmit={handleUploadMaterial} className="space-y-4 py-2">
                        <div className="p-4 border-2 border-dashed border-slate-200 rounded-xl text-center space-y-2 hover:border-primary/50 transition-colors">
                            <UploadCloud className="w-8 h-8 text-primary mx-auto" />
                            <div className="text-xs text-slate-600">
                                <label className="font-semibold text-primary cursor-pointer hover:underline">
                                    Chọn tệp từ thiết bị
                                    <input
                                        type="file"
                                        accept=".pdf,.docx,.txt"
                                        className="hidden"
                                        onChange={(e) => setUploadFile(e.target.files?.[0] || null)}
                                        disabled={isUploading}
                                    />
                                </label>
                                <p className="text-[11px] text-slate-400 mt-1">
                                    Hỗ trợ định dạng PDF, DOCX, TXT (tối đa 20MB)
                                </p>
                            </div>
                            {uploadFile && (
                                <p className="text-xs font-semibold text-slate-800 bg-slate-100 p-2 rounded truncate">
                                    {uploadFile.name} ({(uploadFile.size / (1024 * 1024)).toFixed(2)} MB)
                                </p>
                            )}
                        </div>

                        <DialogFooter className="pt-2">
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => setIsUploadOpen(false)}
                                disabled={isUploading}
                            >
                                Hủy
                            </Button>
                            <Button
                                type="submit"
                                variant="default"
                                disabled={isUploading || !uploadFile}
                            >
                                {isUploading ? "Đang tải lên..." : "Tải lên"}
                            </Button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            </Dialog>

            {/* Create Assignment Modal */}
            <Dialog open={isCreateAssignmentOpen} onOpenChange={setIsCreateAssignmentOpen}>
                <DialogContent className="max-w-lg">
                    <DialogHeader>
                        <DialogTitle className="text-lg font-bold text-slate-900">
                            Tạo bài tập mới cho lớp
                        </DialogTitle>
                    </DialogHeader>

                    <form onSubmit={handleCreateAssignment} className="space-y-4 py-2">
                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Tiêu đề bài tập <span className="text-rose-500">*</span>
                            </label>
                            <Input
                                value={assignTitle}
                                onChange={(e) => setAssignTitle(e.target.value)}
                                placeholder="Ví dụ: Bài tập tuần 3: Lập trình Socket TCP"
                                required
                                disabled={isSavingAssignment}
                            />
                        </div>

                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Yêu cầu & Hướng dẫn làm bài
                            </label>
                            <textarea
                                value={assignInstructions}
                                onChange={(e) => setAssignInstructions(e.target.value)}
                                placeholder="Mô tả chi tiết nội dung, định dạng tệp nộp, tiêu chí chấm..."
                                rows={3}
                                className="w-full text-sm rounded-lg border border-slate-200 p-2.5 focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary disabled:opacity-50"
                                disabled={isSavingAssignment}
                            />
                        </div>

                        <div className="grid grid-cols-2 gap-3">
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-slate-700">
                                    Thang điểm tối đa
                                </label>
                                <Input
                                    type="number"
                                    min={1}
                                    max={100}
                                    value={assignMaxScore}
                                    onChange={(e) => setAssignMaxScore(Number(e.target.value))}
                                    disabled={isSavingAssignment}
                                />
                            </div>
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-slate-700">
                                    Hạn nộp bài <span className="text-rose-500">*</span>
                                </label>
                                <Input
                                    type="datetime-local"
                                    value={assignDueAt}
                                    onChange={(e) => setAssignDueAt(e.target.value)}
                                    required
                                    disabled={isSavingAssignment}
                                />
                            </div>
                        </div>

                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Tệp đính kèm đề bài (tùy chọn)
                            </label>
                            <Input
                                type="file"
                                multiple
                                onChange={(e) => setAssignFiles(Array.from(e.target.files || []))}
                                disabled={isSavingAssignment}
                                className="text-xs"
                            />
                        </div>

                        <label className="flex items-center gap-2 text-xs font-medium text-slate-700 cursor-pointer pt-1">
                            <input
                                type="checkbox"
                                checked={assignAllowLate}
                                onChange={(e) => setAssignAllowLate(e.target.checked)}
                                className="rounded text-primary focus:ring-primary h-4 w-4"
                                disabled={isSavingAssignment}
                            />
                            <span>Cho phép sinh viên nộp muộn sau thời hạn</span>
                        </label>

                        <DialogFooter className="pt-2">
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => setIsCreateAssignmentOpen(false)}
                                disabled={isSavingAssignment}
                            >
                                Hủy
                            </Button>
                            <Button
                                type="submit"
                                variant="default"
                                disabled={isSavingAssignment}
                            >
                                {isSavingAssignment ? "Đang giao bài..." : "Giao bài tập"}
                            </Button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            </Dialog>

            {/* Create Test Modal */}
            <Dialog open={isCreateTestOpen} onOpenChange={setIsCreateTestOpen}>
                <DialogContent className="max-w-md">
                    <DialogHeader>
                        <DialogTitle className="text-lg font-bold text-slate-900">
                            Tạo bài kiểm tra định thời
                        </DialogTitle>
                    </DialogHeader>

                    <form onSubmit={handleCreateTest} className="space-y-4 py-2">
                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Chọn bộ câu hỏi Quiz <span className="text-rose-500">*</span>
                            </label>
                            <select
                                value={selectedQuizId}
                                onChange={(e) => {
                                    const qid = Number(e.target.value);
                                    setSelectedQuizId(qid);
                                    const matched = availableQuizzes.find((q) => q.id === qid);
                                    if (matched) setTestTitle(matched.title);
                                }}
                                className="w-full text-sm rounded-lg border border-slate-200 p-2.5 focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary disabled:opacity-50"
                                required
                                disabled={isSavingTest}
                            >
                                {availableQuizzes.map((quiz) => (
                                    <option key={quiz.id} value={quiz.id}>
                                        {quiz.title} ({quiz.questionCount} câu - {quiz.difficulty})
                                    </option>
                                ))}
                            </select>
                        </div>

                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Tiêu đề bài kiểm tra <span className="text-rose-500">*</span>
                            </label>
                            <Input
                                value={testTitle}
                                onChange={(e) => setTestTitle(e.target.value)}
                                placeholder="Ví dụ: Kiểm tra 15 phút: Tầng giao vận"
                                required
                                disabled={isSavingTest}
                            />
                        </div>

                        <div className="grid grid-cols-2 gap-3">
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-slate-700">
                                    Thời gian mở <span className="text-rose-500">*</span>
                                </label>
                                <Input
                                    type="datetime-local"
                                    value={testStartAt}
                                    onChange={(e) => setTestStartAt(e.target.value)}
                                    required
                                    disabled={isSavingTest}
                                    className="text-xs"
                                />
                            </div>
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-slate-700">
                                    Thời gian đóng <span className="text-rose-500">*</span>
                                </label>
                                <Input
                                    type="datetime-local"
                                    value={testEndAt}
                                    onChange={(e) => setTestEndAt(e.target.value)}
                                    required
                                    disabled={isSavingTest}
                                    className="text-xs"
                                />
                            </div>
                        </div>

                        <div className="grid grid-cols-2 gap-3">
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-slate-700">
                                    Thời lượng (phút)
                                </label>
                                <Input
                                    type="number"
                                    min={1}
                                    max={180}
                                    value={testDuration}
                                    onChange={(e) => setTestDuration(Number(e.target.value))}
                                    required
                                    disabled={isSavingTest}
                                />
                            </div>
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-slate-700">
                                    Số lần làm tối đa
                                </label>
                                <Input
                                    type="number"
                                    min={1}
                                    max={10}
                                    value={testMaxAttempts}
                                    onChange={(e) => setTestMaxAttempts(Number(e.target.value))}
                                    required
                                    disabled={isSavingTest}
                                />
                            </div>
                        </div>

                        <label className="flex items-center gap-2 text-xs font-medium text-slate-700 cursor-pointer pt-1">
                            <input
                                type="checkbox"
                                checked={testShuffle}
                                onChange={(e) => setTestShuffle(e.target.checked)}
                                className="rounded text-primary focus:ring-primary h-4 w-4"
                                disabled={isSavingTest}
                            />
                            <span>Tự động xáo trộn thứ tự câu hỏi cho từng sinh viên</span>
                        </label>

                        <DialogFooter className="pt-2">
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => setIsCreateTestOpen(false)}
                                disabled={isSavingTest}
                            >
                                Hủy
                            </Button>
                            <Button
                                type="submit"
                                variant="default"
                                disabled={isSavingTest}
                            >
                                {isSavingTest ? "Đang tạo..." : "Xác nhận tạo bài kiểm tra"}
                            </Button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            </Dialog>

            {/* Add Co-Teacher Modal */}
            <Dialog open={isAddCoTeacherOpen} onOpenChange={setIsAddCoTeacherOpen}>
                <DialogContent className="max-w-md">
                    <DialogHeader>
                        <DialogTitle className="text-lg font-bold text-slate-900">
                            Thêm giảng viên đồng giảng dạy
                        </DialogTitle>
                    </DialogHeader>

                    <form onSubmit={handleAddCoTeacher} className="space-y-4 py-2">
                        <p className="text-xs text-slate-500">
                            Giảng viên đồng giảng dạy có quyền xem bảng tin, đăng tài liệu, tạo bài tập và chấm điểm sinh viên trong lớp.
                        </p>

                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                ID Giảng viên (User ID) <span className="text-rose-500">*</span>
                            </label>
                            <Input
                                type="number"
                                value={coTeacherUserId}
                                onChange={(e) => setCoTeacherUserId(e.target.value)}
                                placeholder="Ví dụ: 5"
                                required
                                disabled={isAddingCoTeacher}
                            />
                        </div>

                        <DialogFooter className="pt-2">
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => setIsAddCoTeacherOpen(false)}
                                disabled={isAddingCoTeacher}
                            >
                                Hủy
                            </Button>
                            <Button
                                type="submit"
                                variant="default"
                                disabled={isAddingCoTeacher}
                            >
                                {isAddingCoTeacher ? "Đang thêm..." : "Thêm đồng giảng dạy"}
                            </Button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            </Dialog>
        </div>
    );
};

export default ClassDetailPage;
