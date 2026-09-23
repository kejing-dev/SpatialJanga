"""Blender 5.x: original, meter-scale Tower Wood game assets.
Run: Blender --background --python generate_tower.py
"""
import bpy, math, json, os
import numpy as np
from mathutils import Vector

OUT = os.path.dirname(os.path.abspath(__file__))
os.makedirs(os.path.join(OUT, 'textures'), exist_ok=True)
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
scene = bpy.context.scene
scene.unit_settings.system = 'METRIC'
scene.unit_settings.scale_length = 1.0
scene.render.engine = 'CYCLES'
scene.cycles.samples = 48
scene.cycles.use_denoising = True
scene.render.resolution_x = 1400
scene.render.resolution_y = 1400
scene.render.resolution_percentage = 100
scene.world.color = (0.25, 0.25, 0.25)
scene.view_settings.view_transform = 'AgX'

def collection(name):
    c = bpy.data.collections.new(name)
    scene.collection.children.link(c)
    return c

blocks_collection = collection('01_Wood_Blocks_54_Independent')
base_collection = collection('02_Base')
studio_collection = collection('03_Preview_Studio_Not_Exported')

def put(obj, coll):
    for c in list(obj.users_collection): c.objects.unlink(obj)
    coll.objects.link(obj)

# Original procedural texture atlas: long grain on left, end grain on right.
N = 1024
y, x = np.mgrid[0:N, 0:N].astype(np.float32) / N
rng = np.random.default_rng(20260917)
warp = y + .012*np.sin(x*14 + y*9) + .004*np.sin(x*39+y*14)
grain = np.sin(warp*340 + 2*np.sin(warp*37))
fine = np.sin(warp*1350 + np.sin(x*45))
pores = np.maximum(0, grain-.78)**2
field = .035*np.sin(warp*56) + .018*grain + .007*fine - .19*pores
field += rng.normal(0, .004, (N,N))
end = x > .75
ex = (x-.75)/.25
radius = np.sqrt(((ex+.28)*.75)**2 + ((y-.4)*1.4)**2)
endfield = .032*np.sin(radius*85+np.sin(y*19)) + .010*np.sin(radius*330)
field = np.where(end, endfield-.018, field)
rgb = np.clip(np.array([.72,.545,.345])[None,None,:] + field[:,:,None]*np.array([1,.91,.72]),0,1)
rough = np.clip(.48 - field*.8, .35,.65)
gy,gx = np.gradient(field)
normal = np.stack([-gx*2.0,-gy*2.0,np.ones_like(gx)],axis=-1)
normal /= np.linalg.norm(normal,axis=-1)[:,:,None]
normal = normal*.5+.5

def image(name, rgb, noncolor=False):
    im = bpy.data.images.new(name, width=N, height=N, alpha=False)
    im.colorspace_settings.name = 'Non-Color' if noncolor else 'sRGB'
    rgba = np.concatenate([rgb, np.ones((N,N,1),dtype=np.float32)],axis=-1).astype(np.float32)
    im.pixels.foreach_set(rgba.ravel())
    im.filepath_raw = os.path.join(OUT,'textures',name+'.png')
    im.file_format = 'PNG'
    im.save()
    im.pack()
    return im

albedo = image('Wood_Original_BaseColor', rgb)
roughness = image('Wood_Original_Roughness', np.repeat(rough[:,:,None],3,axis=-1),True)
normalmap = image('Wood_Original_Normal', normal,True)

def wood_mat(i):
    m=bpy.data.materials.new('Wood_Grain_%02d'%i)
    m.use_nodes=True
    nt=m.node_tree; p=nt.nodes.get('Principled BSDF')
    p.inputs['Roughness'].default_value=.5
    for im,socket in [(albedo,'Base Color'),(roughness,'Roughness')]:
        t=nt.nodes.new('ShaderNodeTexImage'); t.image=im
        nt.links.new(t.outputs['Color'],p.inputs[socket])
    t=nt.nodes.new('ShaderNodeTexImage');t.image=normalmap
    nm=nt.nodes.new('ShaderNodeNormalMap');nm.inputs['Strength'].default_value=.25
    nt.links.new(t.outputs['Color'],nm.inputs['Color']);nt.links.new(nm.outputs['Normal'],p.inputs['Normal'])
    return m

wood = wood_mat(1)

def cube(name, dims, loc, coll, bevel):
    bpy.ops.mesh.primitive_cube_add(size=1,location=loc)
    o=bpy.context.object;o.name=name;o.dimensions=dims
    bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    if bevel:
        mod=o.modifiers.new('Soft_Edge_Bevel','BEVEL');mod.width=bevel;mod.segments=3
        mod.affect='EDGES'
        bpy.context.view_layer.objects.active=o
        bpy.ops.object.modifier_apply(modifier=mod.name)
    put(o,coll)
    return o

master=cube('Block_L01_S1',(.075,.025,.015),(0,-.025,.0195),blocks_collection,.00045)
master.data.materials.append(wood)
# Atlas coordinates are assigned after bevel. Texture remains simple glTF PBR.
uv=master.data.uv_layers.active or master.data.uv_layers.new(name='UVMap')
for poly in master.data.polygons:
    axis=max(range(3),key=lambda a:abs(poly.normal[a]))
    for li in poly.loop_indices:
        co=master.data.vertices[master.data.loops[li].vertex_index].co
        if axis==0:
            u=.765+(.5+co.y/.025)*.22
            v=.18+(.5+co.z/.015)*.60
        elif axis==1:
            u=.025+(.5+co.x/.075)*.70
            v=.035+(.5+co.z/.015)*.32
        else:
            u=.025+(.5+co.x/.075)*.70
            v=.41+(.5+co.y/.025)*.55
        uv.data[li].uv=(u,v)
for p in master.data.polygons: p.use_smooth=False

# Six UV offsets vary the wood grain while reusing one material/texture set.
meshes=[master.data]
for v in range(1,6):
    me=master.data.copy();me.name='Block_Mesh_UV_Variant_%02d'%v
    for loop in me.uv_layers.active.data:
        if loop.uv.x<.75:
            loop.uv.x=.015+(loop.uv.x-.015)*(.94-v*.008)
            loop.uv.y=.02+(loop.uv.y-.02)*.92+v*.008
    meshes.append(me)
blocks=[]
for layer in range(18):
    for slot in range(3):
        index=layer*3+slot
        if index==0:o=master
        else:
            o=bpy.data.objects.new('Block_L%02d_S%d'%(layer+1,slot+1),meshes[index%6]);blocks_collection.objects.link(o)
        o.rotation_euler.z=math.pi/2 if layer%2 else 0
        offset=(slot-1)*.025
        o.location=(offset if layer%2 else 0,0 if layer%2 else offset,.012+.0075+layer*.015)
        o['block_id']=index;o['layer']=layer+1;o['slot']=slot+1
        o['dimensions_m']=[.075,.025,.015]
        o['runtime_collision']='Box; half-extents 0.0375, 0.0125, 0.0075 m (local axes)'
        o['runtime_mass_kg']=.014
        bpy.context.view_layer.objects.active=o
        o.select_set(True)
        bpy.ops.rigidbody.object_add()
        o.rigid_body.mass=.014;o.rigid_body.collision_shape='BOX'
        o.rigid_body.friction=.6;o.rigid_body.restitution=0.0
        o.rigid_body.use_margin=True;o.rigid_body.collision_margin=.0001
        o.rigid_body.linear_damping=.08;o.rigid_body.angular_damping=.15
        o.select_set(False)
        blocks.append(o)

base=cube('Base_240mm',(.24,.24,.012),(0,0,.006),base_collection,.003)
mat=bpy.data.materials.new('Base_Muted_Sage');mat.diffuse_color=(.16,.205,.17,1);mat.use_nodes=True
mat.node_tree.nodes.get('Principled BSDF').inputs['Base Color'].default_value=(.16,.205,.17,1)
mat.node_tree.nodes.get('Principled BSDF').inputs['Roughness'].default_value=.66
base.data.materials.append(mat)
bpy.context.view_layer.objects.active=base;bpy.ops.rigidbody.object_add();base.rigid_body.type='PASSIVE';base.rigid_body.collision_shape='BOX'
base.rigid_body.use_margin=True;base.rigid_body.collision_margin=.0001;base.rigid_body.friction=.6
scene.rigidbody_world.substeps_per_frame=12
scene.rigidbody_world.solver_iterations=40
scene.frame_end=240

floor=cube('Studio_Ground',(200,200,.01),(0,0,-.006),studio_collection,0)
fm=bpy.data.materials.new('Studio_Warm_Ivory');fm.use_nodes=True
fm.node_tree.nodes.get('Principled BSDF').inputs['Base Color'].default_value=(.61,.59,.53,1)
fm.node_tree.nodes.get('Principled BSDF').inputs['Roughness'].default_value=.85
floor.data.materials.append(fm)

def aim(o,target):o.rotation_euler=(Vector(target)-o.location).to_track_quat('-Z','Y').to_euler()
def area(name,loc,power,size):
    d=bpy.data.lights.new(name,'AREA');o=bpy.data.objects.new(name,d);studio_collection.objects.link(o)
    o.location=loc;d.energy=power;d.shape='DISK';d.size=size;aim(o,(0,0,.13))
area('Key_Softbox',(.35,-.45,.65),18,.5)
area('Fill_Softbox',(-.4,-.05,.4),9,.4)
area('Rim_Softbox',(.15,.35,.5),14,.3)
camdata=bpy.data.cameras.new('Preview_Camera');cam=bpy.data.objects.new('Preview_Camera',camdata);studio_collection.objects.link(cam)
cam.location=(.43,-.62,.42);aim(cam,(0,0,.135));camdata.type='ORTHO';camdata.ortho_scale=.43;scene.camera=cam

def export_glb(filename,objects):
    bpy.ops.object.select_all(action='DESELECT')
    for o in objects:o.select_set(True)
    bpy.context.view_layer.objects.active=objects[0]
    bpy.ops.export_scene.gltf(filepath=os.path.join(OUT,filename),export_format='GLB',use_selection=True,export_yup=True,export_extras=True,export_animations=False)

scene.frame_set(1)
export_glb('tower_54_blocks.glb',blocks+[base])
original=master.location.copy();master.location=(0,0,0)
export_glb('wood_block.glb',[master])
master.location=original
export_glb('base.glb',[base])

report={'units':'meters','blender_up_axis':'Z','glb_up_axis':'Y','block_count':len(blocks),'layers':18,'blocks_per_layer':3,
 'block_dimensions_m':[.075,.025,.015],'bevel_m':.00045,'base_dimensions_m':[.24,.24,.012],
 'tower_height_without_base_m':.270,'tower_height_with_base_m':.282,
 'triangles_per_block':sum(len(p.vertices)-2 for p in master.data.polygons),
 'original_textures':True,'texture_resolution':N,
 'runtime_note':'glTF carries appearance, transforms and extras. Blender rigid bodies are not exported as a working physics system; configure Box colliders and constraints in the app.',
 'checks':{'block_count':len(blocks)==54,'independent_objects':len({o.name for o in blocks})==54,'unit_scale':all(tuple(o.scale)==(1,1,1) for o in blocks)}}
assert all(report['checks'].values())
with open(os.path.join(OUT,'asset_manifest.json'),'w') as fh:json.dump(report,fh,indent=2)

bpy.ops.object.select_all(action='DESELECT')
for o in blocks:o.select_set(True)
bpy.context.view_layer.objects.active=master
# Set useful solid/material viewport and camera framing for first open.
for screen in bpy.data.screens:
    for a in screen.areas:
        if a.type=='VIEW_3D':
            a.spaces.active.region_3d.view_distance=.6
            a.spaces.active.region_3d.view_location=(0,0,.14)
            a.spaces.active.clip_start=.001
            a.spaces.active.shading.type='MATERIAL'
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(OUT,'tower_wood.blend'))
scene.render.filepath=os.path.join(OUT,'tower_preview.png');bpy.ops.render.render(write_still=True)

# Asset detail view, separately rendered without changing the saved game layout.
for o in blocks:o.hide_render=True
base.hide_render=True
master.hide_render=False;master.location=(0,0,.0075);master.rotation_euler=(0,0,0)
cam.location=(.12,-.16,.10);aim(cam,(0,0,.0075));camdata.ortho_scale=.12
scene.render.resolution_x=1400;scene.render.resolution_y=900
scene.render.filepath=os.path.join(OUT,'block_preview.png');bpy.ops.render.render(write_still=True)
print('ASSET_COMPLETE',json.dumps(report))
