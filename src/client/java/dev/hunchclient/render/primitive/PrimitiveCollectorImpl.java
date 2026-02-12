package dev.hunchclient.render.primitive;

import dev.hunchclient.render.state.CameraRenderState;
import dev.hunchclient.render.state.FilledBoxRenderState;
import dev.hunchclient.render.state.LinesRenderState;
import dev.hunchclient.render.state.OutlinedBoxRenderState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Primitive collector implementation - 1:1 from Skyblocker
 */
public final class PrimitiveCollectorImpl implements PrimitiveCollector {
    private List<FilledBoxRenderState> filledBoxStates = null;
    private List<OutlinedBoxRenderState> outlinedBoxStates = null;
    private List<LinesRenderState> linesStates = null;
    private boolean frozen = false;

    public PrimitiveCollectorImpl() {}

    @Override
    public void submitFilledBox(BlockPos pos, float[] colourComponents, float alpha, boolean throughWalls) {
        submitFilledBox(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, colourComponents, alpha, throughWalls);
    }

    @Override
    public void submitFilledBox(Vec3 pos, Vec3 dimensions, float[] colourComponents, float alpha, boolean throughWalls) {
        submitFilledBox(pos.x, pos.y, pos.z, pos.x + dimensions.x, pos.y + dimensions.y, pos.z + dimensions.z, colourComponents, alpha, throughWalls);
    }

    @Override
    public void submitFilledBox(AABB box, float[] colourComponents, float alpha, boolean throughWalls) {
        submitFilledBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, colourComponents, alpha, throughWalls);
    }

    private void submitFilledBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, float[] colourComponents, float alpha, boolean throughWalls) {
        ensureNotFrozen();

        if (this.filledBoxStates == null) {
            this.filledBoxStates = new ArrayList<>();
        }

        FilledBoxRenderState state = new FilledBoxRenderState();
        state.minX = minX;
        state.minY = minY;
        state.minZ = minZ;
        state.maxX = maxX;
        state.maxY = maxY;
        state.maxZ = maxZ;
        state.colourComponents = colourComponents;
        state.alpha = alpha;
        state.throughWalls = throughWalls;

        this.filledBoxStates.add(state);
    }

    @Override
    public void submitOutlinedBox(BlockPos pos, float[] colourComponents, float lineWidth, boolean throughWalls) {
        submitOutlinedBox(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, colourComponents, 1f, lineWidth, throughWalls);
    }

    @Override
    public void submitOutlinedBox(AABB box, float[] colourComponents, float lineWidth, boolean throughWalls) {
        submitOutlinedBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, colourComponents, 1f, lineWidth, throughWalls);
    }

    @Override
    public void submitOutlinedBox(AABB box, float[] colourComponents, float alpha, float lineWidth, boolean throughWalls) {
        submitOutlinedBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, colourComponents, alpha, lineWidth, throughWalls);
    }

    private void submitOutlinedBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, float[] colourComponents, float alpha, float lineWidth, boolean throughWalls) {
        ensureNotFrozen();

        if (this.outlinedBoxStates == null) {
            this.outlinedBoxStates = new ArrayList<>();
        }

        OutlinedBoxRenderState state = new OutlinedBoxRenderState();
        state.minX = minX;
        state.minY = minY;
        state.minZ = minZ;
        state.maxX = maxX;
        state.maxY = maxY;
        state.maxZ = maxZ;
        state.colourComponents = colourComponents;
        state.alpha = alpha;
        state.lineWidth = lineWidth;
        state.throughWalls = throughWalls;

        this.outlinedBoxStates.add(state);
    }

    @Override
    public void submitLinesFromPoints(Vec3[] points, float[] colourComponents, float alpha, float lineWidth, boolean throughWalls) {
        ensureNotFrozen();

        if (this.linesStates == null) {
            this.linesStates = new ArrayList<>();
        }

        LinesRenderState state = new LinesRenderState();
        state.points = points;
        state.colourComponents = colourComponents;
        state.alpha = alpha;
        state.lineWidth = lineWidth;
        state.throughWalls = throughWalls;

        this.linesStates.add(state);
    }

    public void endCollection() {
        this.frozen = true;
    }

    private void ensureNotFrozen() {
        if (this.frozen) {
            throw new IllegalStateException("Cannot submit primitives once the collection phase has ended!");
        }
    }

    public void dispatchPrimitivesToRenderers(CameraRenderState cameraState) {
        if (!this.frozen) {
            throw new IllegalStateException("Cannot dispatch primitives until the collection phase has ended!");
        }

        if (this.filledBoxStates != null) {
            for (FilledBoxRenderState state : this.filledBoxStates) {
                FilledBoxRenderer.INSTANCE.submitPrimitives(state, cameraState);
            }
        }

        if (this.outlinedBoxStates != null) {
            for (OutlinedBoxRenderState state : this.outlinedBoxStates) {
                OutlinedBoxRenderer.INSTANCE.submitPrimitives(state, cameraState);
            }
        }

        if (this.linesStates != null) {
            for (LinesRenderState state : this.linesStates) {
                LinesRenderer.INSTANCE.submitPrimitives(state, cameraState);
            }
        }
    }
}
