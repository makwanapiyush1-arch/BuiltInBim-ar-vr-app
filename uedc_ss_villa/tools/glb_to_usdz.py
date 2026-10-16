"""Convert a simple .glb (meshes + base colours, no textures) to .usdz for the iOS app.

Usage:  python glb_to_usdz.py model.glb model.usdz
Needs:  pip install usd-core pygltflib numpy

For real models with textures use Blender (File > Export > Universal Scene Description, .usdz)
or Apple's Reality Converter instead; this script is what produced the bundled placeholder.
"""
import sys

import numpy as np
import pygltflib
from pxr import Gf, Sdf, Usd, UsdGeom, UsdShade, UsdUtils

COMPONENTS = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}
DTYPES = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}


def read_accessor(gltf, blob, index):
    acc = gltf.accessors[index]
    view = gltf.bufferViews[acc.bufferView]
    n = COMPONENTS[acc.type]
    dtype = np.dtype(DTYPES[acc.componentType])
    start = (view.byteOffset or 0) + (acc.byteOffset or 0)
    stride = view.byteStride or dtype.itemsize * n
    rows = [np.frombuffer(blob, dtype, n, start + i * stride) for i in range(acc.count)]
    return np.array(rows).reshape(acc.count, n)


def node_matrix(node):
    if node.matrix:
        return Gf.Matrix4d(*node.matrix)
    m = Gf.Matrix4d(1)
    if node.scale:
        m = m * Gf.Matrix4d().SetScale(Gf.Vec3d(*node.scale))
    if node.rotation:
        x, y, z, w = node.rotation
        m = m * Gf.Matrix4d().SetRotate(Gf.Quatd(w, x, y, z))
    if node.translation:
        m = m * Gf.Matrix4d().SetTranslate(Gf.Vec3d(*node.translation))
    return m


def main(src, dst):
    gltf = pygltflib.GLTF2().load(src)
    blob = gltf.binary_blob()
    usda = dst.rsplit(".", 1)[0] + ".usdc"
    stage = Usd.Stage.CreateNew(usda)
    UsdGeom.SetStageUpAxis(stage, UsdGeom.Tokens.y)
    UsdGeom.SetStageMetersPerUnit(stage, 1.0)
    root = UsdGeom.Xform.Define(stage, "/Model")
    stage.SetDefaultPrim(root.GetPrim())

    materials = []
    for i, mat in enumerate(gltf.materials):
        r, g, b, a = (mat.pbrMetallicRoughness.baseColorFactor or [0.8, 0.8, 0.8, 1]) if mat.pbrMetallicRoughness else [0.8] * 3 + [1]
        m = UsdShade.Material.Define(stage, f"/Model/Materials/M{i}")
        shader = UsdShade.Shader.Define(stage, f"/Model/Materials/M{i}/Surface")
        shader.CreateIdAttr("UsdPreviewSurface")
        shader.CreateInput("diffuseColor", Sdf.ValueTypeNames.Color3f).Set(Gf.Vec3f(r, g, b))
        shader.CreateInput("opacity", Sdf.ValueTypeNames.Float).Set(a)
        shader.CreateInput("roughness", Sdf.ValueTypeNames.Float).Set(0.8)
        m.CreateSurfaceOutput().ConnectToSource(shader.ConnectableAPI(), "surface")
        materials.append(m)

    counter = [0]

    def visit(node_index, parent_path):
        node = gltf.nodes[node_index]
        counter[0] += 1
        path = f"{parent_path}/N{counter[0]}"
        xf = UsdGeom.Xform.Define(stage, path)
        xf.AddTransformOp().Set(node_matrix(node))
        if node.mesh is not None:
            for j, prim in enumerate(gltf.meshes[node.mesh].primitives):
                pts = read_accessor(gltf, blob, prim.attributes.POSITION)
                idx = read_accessor(gltf, blob, prim.indices).flatten() if prim.indices is not None else np.arange(len(pts))
                mesh = UsdGeom.Mesh.Define(stage, f"{path}/P{j}")
                mesh.CreatePointsAttr([Gf.Vec3f(*p) for p in pts.tolist()])
                mesh.CreateFaceVertexCountsAttr([3] * (len(idx) // 3))
                mesh.CreateFaceVertexIndicesAttr(idx.tolist())
                mesh.CreateSubdivisionSchemeAttr("none")
                if prim.attributes.NORMAL is not None:
                    nrm = read_accessor(gltf, blob, prim.attributes.NORMAL)
                    mesh.CreateNormalsAttr([Gf.Vec3f(*n) for n in nrm.tolist()])
                    mesh.SetNormalsInterpolation(UsdGeom.Tokens.vertex)
                mesh.CreateDoubleSidedAttr(True)
                if prim.material is not None:
                    UsdShade.MaterialBindingAPI.Apply(mesh.GetPrim()).Bind(materials[prim.material])
        for child in node.children or []:
            visit(child, path)

    for n in gltf.scenes[gltf.scene or 0].nodes:
        visit(n, "/Model")
    stage.GetRootLayer().Save()
    if not UsdUtils.CreateNewUsdzPackage(Sdf.AssetPath(usda), dst):
        sys.exit("usdz packaging failed")
    print("wrote", dst)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
