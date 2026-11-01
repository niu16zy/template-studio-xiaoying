import { Suspense } from "react";
import { TemplateGallery } from "@/features/templates/template-gallery";

export default function HomePage() {
  return (
    <main id="main">
      <section className="intro">
        <div><p className="eyebrow">A LITTLE INSPIRATION, EVERY DAY</p><h1>让灵感，有画面。</h1></div>
        <p className="intro-note">从一个模板开始，留住你的日常。<br />选择身份，上传属于你的素材。</p>
      </section>
      <Suspense fallback={<p role="status">正在准备模板列表…</p>}>
        <TemplateGallery />
      </Suspense>
    </main>
  );
}
