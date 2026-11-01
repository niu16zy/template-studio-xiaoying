import { TemplateDetail } from "@/features/templates/template-detail";

export default async function TemplatePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <TemplateDetail key={id} id={id} />;
}
