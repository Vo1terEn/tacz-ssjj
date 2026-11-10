"""Presentation changes explicitly requested by the user; source exports stay intact."""
def apply_original_effect_overrides(data):
    groups=[g for g in data['groups'] if g['name']=='native_absorb_chouqu']
    assert len(groups)==1
    flame=groups[0]
    assert flame['sourceEffect']=='E015' and flame['bone']==0
    flame.update(hitOnly=False,bodyLoop=True,trigger='body_loop',loop=True,prewarm=True)
    data['user_body_flame_override']={
        'requested_date':'2026-11-02',
        'layer':'native_absorb_chouqu',
        'behavior':'Continuous gun-body loop; independent of shot and hit frequency.',
        'preserved':'Source geometry, gun mount, texture, color, lifetime, rate and live-particle cap; laser and hit-feedback glows retain their triggers.',
    }
    return data
