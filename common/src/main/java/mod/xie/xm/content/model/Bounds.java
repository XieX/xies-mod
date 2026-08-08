package mod.xie.xm.content.model;

/**
 * An axis-aligned box in block-local space, parsed from a
 * {@code "x1,y1,z1,x2,y2,z2"} string.
 *
 * <p>The registrar turns this into a {@code VoxelShape}. Kept as bare floats here
 * so the model stays free of Minecraft types.
 */
public record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {

    public static final Bounds FULL_BLOCK = new Bounds(0f, 0f, 0f, 1f, 1f, 1f);

    public Bounds {
        if (minX > maxX) {
            float swap = minX;
            minX = maxX;
            maxX = swap;
        }
        if (minY > maxY) {
            float swap = minY;
            minY = maxY;
            maxY = swap;
        }
        if (minZ > maxZ) {
            float swap = minZ;
            minZ = maxZ;
            maxZ = swap;
        }
    }

    /** Same box expressed in the 0..16 pixel units that Minecraft's shape helpers use. */
    public float[] toPixels() {
        return new float[] {
            minX * 16f, minY * 16f, minZ * 16f,
            maxX * 16f, maxY * 16f, maxZ * 16f
        };
    }

    public boolean isFullBlock() {
        return equals(FULL_BLOCK);
    }

    @Override
    public String toString() {
        return minX + "," + minY + "," + minZ + "," + maxX + "," + maxY + "," + maxZ;
    }
}
