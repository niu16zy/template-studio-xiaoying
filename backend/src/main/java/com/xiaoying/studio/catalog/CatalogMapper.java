package com.xiaoying.studio.catalog;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CatalogMapper {
    @Select("SELECT id, name, sort_order FROM template_category ORDER BY sort_order, id")
    List<Category> categories();

    @Select("""
        <script>
        SELECT id, category_id, title, description, cover_path, sample_path,
               required_image_count, accepted_mime_types, max_file_size_bytes
        FROM template
        <if test="categoryId != null">WHERE category_id = #{categoryId}</if>
        ORDER BY sort_order, id
        </script>
        """)
    List<TemplateRow> templates(@Param("categoryId") String categoryId);

    @Select("""
        SELECT id, category_id, title, description, cover_path, sample_path,
               required_image_count, accepted_mime_types, max_file_size_bytes
        FROM template WHERE id = #{id}
        """)
    TemplateRow template(@Param("id") String id);

    @Select("""
        SELECT COUNT(*) FROM template
        WHERE cover_path = #{name} OR sample_path = #{name} OR result_source_path = #{name}
        """)
    int publicAssetReferences(@Param("name") String name);
}
