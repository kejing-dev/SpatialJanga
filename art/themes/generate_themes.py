import bpy, os, json, math
from mathutils import Vector
OUT=os.path.dirname(os.path.abspath(__file__))
bpy.ops.wm.open_mainfile(filepath=os.path.join(OUT,'../tower_wood.blend'))
scene=bpy.context.scene
blocks=sorted([o for o in bpy.data.objects if 'block_id' in o],key=lambda o:o['block_id'])
original=blocks[0].data.materials[0]
palette={'walnut':'694832','charcoal':'353A40','ivory':'F1EADF','rose':'E7ADAD','peach':'EBC8AA','butter':'E7DDA9','mint':'B6D2BC','milkblue':'B6CFE4','lavender':'CCBFDD'}
materials={'natural':original}
for name,h in palette.items():
 rgb=[int(h[i:i+2],16)/255 for i in (0,2,4)]
 # A subtle original grain texture is retained beneath the painted finish.
 src=bpy.data.images['Wood_Original_BaseColor']; src.scale(256,256) if src.size[0]!=256 else None
 import numpy as np
 pixels=np.array(src.pixels[:],dtype=np.float32).reshape(256,256,4)
 grain=pixels[:,:,:3].mean(axis=2); grain=(grain-grain.mean())*.28
 painted=np.ones((256,256,4),dtype=np.float32);painted[:,:,:3]=np.clip(np.array(rgb)[None,None,:]+grain[:,:,None],0,1)
 image=bpy.data.images.new(name+'_grain',width=256,height=256,alpha=False);image.pixels.foreach_set(painted.ravel());image.filepath_raw=os.path.join(OUT,name+'.png');image.file_format='PNG';image.save();image.pack()
 mat=bpy.data.materials.new('Theme_'+name);mat.use_nodes=True;p=mat.node_tree.nodes.get('Principled BSDF');p.inputs['Roughness'].default_value=.62
 tex=mat.node_tree.nodes.new('ShaderNodeTexImage');tex.image=image;mat.node_tree.links.new(tex.outputs['Color'],p.inputs['Base Color']);materials[name]=mat
master=blocks[0];loc=master.location.copy();rot=master.rotation_euler.copy()
for name,mat in materials.items():
 if name=='natural':continue
 master.material_slots[0].link='OBJECT';master.material_slots[0].material=mat
 master.location=(0,0,0);master.rotation_euler=(0,0,0)
 bpy.ops.object.select_all(action='DESELECT');master.select_set(True);bpy.context.view_layer.objects.active=master
 bpy.ops.export_scene.gltf(filepath=os.path.join(OUT,name+'.glb'),export_format='GLB',use_selection=True,export_yup=True,export_animations=False)
master.location=loc;master.rotation_euler=rot
sets={'natural':['natural'],'walnut':['walnut'],'mono':['charcoal','ivory'],'pastel':['rose','peach','butter','mint','milkblue','lavender']}
scene.render.resolution_x=320;scene.render.resolution_y=280;scene.render.resolution_percentage=100;scene.cycles.samples=24
for theme,names in sets.items():
 for o in blocks:
  o.material_slots[0].link='OBJECT';o.material_slots[0].material=materials[names[o['block_id']//3%len(names)]]
 scene.render.filepath=os.path.join(OUT,'theme_'+theme+'.png');bpy.ops.render.render(write_still=True)
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(OUT,'tower_palettes.blend'))
json.dump({'themes':sets,'palette_srgb':palette,'alpha':1,'dimensions_m':[.075,.015,.025],'source':'Original Tower Wood Blender mesh and procedural grain','preview':'320x280 Cycles; same layer coloring as runtime'},open(os.path.join(OUT,'manifest.json'),'w'),indent=2)
