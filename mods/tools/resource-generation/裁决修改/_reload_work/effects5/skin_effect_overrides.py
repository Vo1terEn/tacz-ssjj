"""Explicit presentation overrides requested by the user; original source data stays intact."""
FROZEN_SIDE_DISCS={
    'body_continuous/Particle_duplicate_2':790748140386550604,
    'head_continuous/Particle':-2054808698939854081,
}
FROZEN_DISC_TEXTURE='e6892a0ec8bb57ebb5bb20a6653dfebf5f07848dfe0b59a4332031a7bafc3836'

def apply_skin_body_overrides(skin,data):
    """Disable only frozen's two paired-disc source meshes, at both generation boundaries."""
    if skin!='frozen':return []
    assert data['skin']=='frozen'
    matched=[]
    for layer in data['groups']:
        if layer['name'] not in FROZEN_SIDE_DISCS:continue
        assert layer['sourceNodeId']==FROZEN_SIDE_DISCS[layer['name']],layer['name']
        material=data['materials'][layer['material']]
        assert any(t.get('slot')=='_MainTex' and t.get('sha256')==FROZEN_DISC_TEXTURE
                   for t in material['textureSources']),layer['name']
        assert len(layer['nativeLocalVertices'])==12 and len(layer['faces'])==4,layer['name']
        layer['enabled']=False
        layer['userDisableReason']='User request 2026-11-02: remove the unwanted blue discs on both sides of the frozen gun.'
        matched.append(layer['name'])
    assert set(matched)==set(FROZEN_SIDE_DISCS),matched
    data.setdefault('userOverrides',{})['frozen_side_disc_removal']={
        'requested_date':'2026-11-02',
        'disabled_layers':matched,
        'reason':'Remove paired blue ice-disc planes on the gun body and head; preserve all other source effects.',
        'source_mesh_material_texture_values_preserved':True,
    }
    layers=[layer for kind in ('groups','emitters')for layer in data[kind]]
    data.setdefault('validation',{})['user_visible_layers']={
        'authored_layers':len(layers),'active_layers':sum(bool(x['enabled'] and x['source_active']) for x in layers),
        'disabled_side_disc_layers':len(matched),
    }
    return matched
