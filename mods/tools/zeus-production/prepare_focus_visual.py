"""Reuse the supplied Zeus II skill icon without modifying any pixels."""
from pathlib import Path
import shutil
from PIL import Image
from prepare import W,ROOT,save,sha
def main():
 folder=next((ROOT/'宙斯、聚能/宙斯之手/完整源素材/武器').glob('*__hand_of_zeuss2'))/'hand_of_zeuss2'
 source=next((folder/'Textures').glob('activeIcon__*.png'))
 layout=next((folder/'EmbeddedData').glob('ui_layout__*.bytes'))
 atlas=next((folder/'EmbeddedData').glob('uitextureconfig__*.bytes'))
 assert Image.open(source).size==(22,22)
 assert b'/weapon/hand_of_zeuss2/ui/unactiveIcon' in layout.read_bytes()
 assert b'name="activeIcon"' in atlas.read_bytes()and b'width="22" height="22"' in atlas.read_bytes()
 dest=W/'pack/assets/ssjj_zeus/textures/focus/source_marker.png';dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest)
 assert sha(source)==sha(dest)
 save(W/'evidence/focus_visual_prepare.json',{'passed':True,'source':source.as_posix(),'source_role':'Original Zeus II active skill-charge icon. No dedicated target/lock marker was found in the supplied Zeus textures or UI atlas. Reused as the owner target marker, not claimed to be the original target marker.','image_size':[22,22],'byte_identical':True,'render_adaptation':'Unmodified full image, 0.6-block camera-facing quad above the interpolated entity head. Standard entityCutoutNoCull depth test and actual environmental light; no emissive/outline/see-through pass or new effect/audio.','input_sha256':{p.as_posix():sha(p)for p in [source,layout,atlas,Path(__file__)]},'output_sha256':{dest.relative_to(W).as_posix():sha(dest)},'live_client_test':False})
 print('PASS unmodified supplied Zeus II skill icon as depth-tested focus marker')
if __name__=='__main__':main()
