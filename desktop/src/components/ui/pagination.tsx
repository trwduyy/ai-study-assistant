import React from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "./button";

export interface PaginationProps {
    page?: number;
    currentPage?: number;
    totalPages: number;
    onPageChange: (page: number) => void;
}

export const Pagination: React.FC<PaginationProps> = ({
    page,
    currentPage,
    totalPages,
    onPageChange
}) => {
    const activePage = page !== undefined ? page : (currentPage !== undefined ? currentPage : 0);

    return (
        <nav className="flex items-center justify-between gap-3" aria-label="Phân trang">
            <p className="text-sm text-muted-foreground">
                Trang {totalPages === 0 ? 0 : activePage + 1} trên {totalPages}
            </p>
            <div className="flex gap-2">
                <Button
                    variant="outline"
                    size="sm"
                    onClick={() => onPageChange(activePage - 1)}
                    disabled={activePage <= 0}
                    aria-label="Trang trước"
                >
                    <ChevronLeft className="h-4 w-4" />
                    <span>Trước</span>
                </Button>
                <Button
                    variant="outline"
                    size="sm"
                    onClick={() => onPageChange(activePage + 1)}
                    disabled={totalPages === 0 || activePage >= totalPages - 1}
                    aria-label="Trang sau"
                >
                    <span>Sau</span>
                    <ChevronRight className="h-4 w-4" />
                </Button>
            </div>
        </nav>
    );
};

export default Pagination;
