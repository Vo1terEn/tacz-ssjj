"""Apply the source-screenshot camera fit through existing native view nodes."""
from prepare import W,read,save,sha
def main():
    fit=read(W/'evidence/fit_reference.json');assert fit['passed']
    a=W/'pack/assets/ssjj_zeus';geo=read(a/'geo_models/gun/hand_of_zeuss2_geo.json')
    for b in geo['minecraft:geometry'][0]['bones']:
        if b['name'] in ('idle_view','iron_view','scope_pos'):
            b['pivot']=fit['geo_idle_view_pivot'];b['rotation']=fit['geo_idle_view_rotation_degrees']
    save(a/'geo_models/gun/hand_of_zeuss2_geo.json',geo)
    cal=read(a/'view_calibration.json');cal.pop('framing_reference',None)
    cal.update({'status':'SOURCE_SCREENSHOT_RIGID_BODY_PERSPECTIVE_FIT',
        'idle_eye_native_blocks':fit['idle_eye_native_blocks'],
        'idle_view_rotation_degrees':fit['geo_idle_view_rotation_degrees'],
        'expected_complete_matrix_on_native_emitted':fit['new_complete_matrix'],
        'fitted_vertical_fov_degrees':fit['vertical_fov_degrees'],'reference_exact':False,
        'reference_image_sha256':sha(W/'reference/user_source.png'),
        'fit_boundary':'Rigid-body texture/shape fit to the supplied source screenshot. Lighting, particles, source character fingers and UI excluded; native block hands checked separately.',
        'source_screenshot_fit':{'feature_points':fit['points'],'rms_pixels_1920x1080':fit['rms_pixels_1920x1080'],'maximum_pixels':fit['max_pixels']}})
    save(a/'view_calibration.json',cal)
    display=read(a/'display/guns/hand_of_zeuss2_display.json');display['zoom_model_fov']=fit['vertical_fov_degrees'];save(a/'display/guns/hand_of_zeuss2_display.json',display)
    save(W/'evidence/camera_applied.json',{'passed':True,'fit_sha256':sha(W/'evidence/fit_reference.json'),'source_image_sha256':sha(W/'reference/user_source.png'),'native_view_nodes':['idle_view','iron_view','scope_pos'],'new_hook':False,'body_mesh_scale_changed':False,'live_client_test':False})
    print('Applied supplied-source screenshot fit via existing view nodes and FOV data')
if __name__=='__main__':main()
