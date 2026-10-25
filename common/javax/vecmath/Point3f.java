// STUB(R.Chen): javax.vecmath.Point3f → org.joml.Vector3f shim.
package javax.vecmath;

public class Point3f extends org.joml.Vector3f {
    public Point3f() {}
    public Point3f(float x, float y, float z) { super(x, y, z); this.x = x; this.y = y; this.z = z; }
    @Override
    public Point3f set(float x, float y, float z) { super.set(x, y, z); return this; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }
}
