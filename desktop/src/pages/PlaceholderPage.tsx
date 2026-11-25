import React from "react";
import { Construction } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";

interface PlaceholderPageProps {
    title: string;
    description: string;
}

export const PlaceholderPage: React.FC<PlaceholderPageProps> = ({ title, description }) => (
    <Card className="mx-auto max-w-2xl">
        <CardHeader><CardTitle>{title}</CardTitle></CardHeader>
        <CardContent className="flex items-start gap-4">
            <Construction className="h-7 w-7 shrink-0 text-info" aria-hidden="true" />
            <p className="text-sm leading-6 text-muted-foreground">{description} Chức năng này đang được hoàn thiện và sẽ sẵn sàng trong đợt phát triển tiếp theo.</p>
        </CardContent>
    </Card>
);
