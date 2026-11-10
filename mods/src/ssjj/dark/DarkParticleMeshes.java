package ssjj.dark;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.LinkedHashMap;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

/** Render-thread-only, bounded LRU of the original particle triangles. The cache
 * never drops geometry; a miss uploads exactly the source batch. */
final class DarkParticleMeshes {
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final int MAX_ENTRIES = 192;
    private record Mesh(VertexBuffer buffer, int bytes) {}
    private static final LinkedHashMap<DarkRuntimeAssets.Batch, Mesh> CACHE = new LinkedHashMap<>(192, .75f, true);
    private static long bytes;
    private static final Matrix4f IDENTITY = new Matrix4f();

    static void draw(DarkRuntimeAssets.Batch batch, VertexFormat format, Matrix4f projection, ShaderInstance shader) {
        Mesh mesh = CACHE.get(batch);
        if (mesh == null) {
            float[] vertices = batch.vertices();
            int size = vertices.length / 9 * format.m_86020_();
            while (!CACHE.isEmpty() && (bytes + size > MAX_BYTES || CACHE.size() >= MAX_ENTRIES)) {
                var oldest = CACHE.entrySet().iterator();
                var removed = oldest.next().getValue(); oldest.remove();
                bytes -= removed.bytes(); removed.buffer().close();
            }
            BufferBuilder buffer = Tesselator.m_85913_().m_85915_();
            buffer.m_166779_(VertexFormat.Mode.TRIANGLES, format);
            for (int i = 0; i < vertices.length; i += 9)
                buffer.m_5483_(vertices[i], vertices[i + 1], vertices[i + 2])
                    .m_7421_(vertices[i + 3], vertices[i + 4])
                    .m_85950_(vertices[i + 5], vertices[i + 6], vertices[i + 7], vertices[i + 8]).m_5752_();
            VertexBuffer uploaded = new VertexBuffer(VertexBuffer.Usage.STATIC);
            uploaded.m_85921_(); uploaded.m_231221_(buffer.m_231175_()); VertexBuffer.m_85931_();
            mesh = new Mesh(uploaded, size);
            // Oversized custom-resource batches still draw in full, but are not
            // retained beyond this draw, keeping the cache's hard memory cap.
            if (size <= MAX_BYTES) { CACHE.put(batch, mesh); bytes += size; }
        }
        mesh.buffer().m_85921_();
        // The author's native_skill vertex shader uses ProjMat only. Fold the
        // camera/source transform into projection instead of changing shaders.
        try { mesh.buffer().m_253207_(IDENTITY, projection, shader); }
        finally { VertexBuffer.m_85931_(); if (mesh.bytes() > MAX_BYTES) mesh.buffer().close(); }
    }

    static void clear() {
        if (!RenderSystem.isOnRenderThread()) { RenderSystem.recordRenderCall(DarkParticleMeshes::clear); return; }
        for (Mesh mesh : CACHE.values()) mesh.buffer().close();
        CACHE.clear(); bytes = 0;
    }
    private DarkParticleMeshes() {}
}
