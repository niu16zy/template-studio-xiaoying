"use client";

import Image from "next/image";
import Link from "next/link";
import { useEffect, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { getJson } from "@/lib/api";
import type { Category, Template } from "@/lib/contracts";

type CatalogState =
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "ready"; categories: Category[]; templates: Template[] };

export function TemplateGallery() {
  const router = useRouter();
  const pathname = usePathname();
  const search = useSearchParams();
  const category = search.get("category") ?? "";
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<CatalogState>({ status: "loading" });

  useEffect(() => {
    const controller = new AbortController();
    let current = true;
    const suffix = category ? `?categoryId=${encodeURIComponent(category)}` : "";
    Promise.all([
      getJson<Category[]>("/api/categories", controller.signal),
      getJson<Template[]>(`/api/templates${suffix}`, controller.signal),
    ]).then(([categories, templates]) => {
      if (current) setState({ status: "ready", categories, templates });
    }).catch((error: unknown) => {
      if (current) setState({
        status: "error",
        message: error instanceof Error ? error.message : "模板加载失败",
      });
    });
    return () => { current = false; controller.abort(); };
  }, [category, attempt]);

  function selectCategory(id: string) {
    if (id === category) return;
    setState({ status: "loading" });
    router.replace(id ? `${pathname}?category=${encodeURIComponent(id)}` : pathname, { scroll: false });
  }

  if (state.status === "loading") return (
    <section aria-label="正在加载模板" aria-busy="true">
      <p className="muted" role="status">正在加载模板…</p>
      <div className="template-grid">{Array.from({ length: 6 }, (_, i) => <div key={i} className="skeleton" />)}</div>
    </section>
  );
  if (state.status === "error") return (
    <section className="notice" role="alert">
      <h2>暂时无法获取模板</h2><p>{state.message}</p>
      <button className="primary-button" onClick={() => { setState({ status: "loading" }); setAttempt((a) => a + 1); }}>重新加载</button>
    </section>
  );

  return (
    <section aria-label="模板库">
      <div className="catalog-toolbar">
        <div className="category-list" role="group" aria-label="按分类筛选">
          {[{ id: "", name: "全部灵感" }, ...state.categories].map((item) => (
            <button key={item.id} aria-pressed={category === item.id}
              onClick={() => selectCategory(item.id)}>{item.name}</button>
          ))}
        </div>
        <span className="muted">{state.templates.length} 个模板</span>
      </div>
      {state.templates.length === 0 ? <div className="notice"><h2>这个分类还没有模板</h2><button onClick={() => selectCategory("")}>查看全部</button></div> : (
        <div className="template-grid">
          {state.templates.map((template, index) => (
            <article className="template-card" key={template.id}>
              <Link className="cover template-link" href={"/templates/" + template.id} aria-label={"打开模板：" + template.title}>
                <Image src={template.coverUrl} alt={template.title + "示例封面"} fill unoptimized
                  sizes="(max-width: 600px) 50vw, (max-width: 1000px) 33vw, 17vw"
                  priority={index < 2} />
                <span className="image-count">{template.requiredImageCount} 张图片</span>
                <div className="cover-caption"><h2>{template.title}</h2><span>准备素材 <span aria-hidden="true">↗</span></span></div>
              </Link>
              <p>{template.description}</p>
            </article>
          ))}
        </div>
      )}
      <p className="phase-note">选择模板、上传图片，后台制作会自动继续。随时前往“我的记录”查看进度与结果。</p>
    </section>
  );
}
