"""Remove only source tail emitters and correct audio using reference-video evidence."""
from pathlib import Path
import copy,json,shutil,subprocess
from prepare import W,ROOT,save,read,sha
BASE=W/'baseline_assets';PACK=W/'pack';A='assets/ssjj_zeus';KEEP={'shandianqiu','shandian01','dianqiu','shandian','guangquan','shandian02'}
TAIL={'hand_of_zeuss1_orb':{'tuowei','lizi01','kuosan02'},'hand_of_zeuss2_orb':{'tuowei','lizi01','kuosan02'},'lava_orb':{'tuowei','tuowei_duplicate_2','lizi01','qitou'}}
def compact(path,value):
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,separators=(',',':'),allow_nan=False)+'\n','utf8')
def main():
 records=[];inputs={};outputs={}
 for name,tail in TAIL.items():
  rel=f'{A}/effects/thunder/{name}.json';src=BASE/rel;old=read(src);names={e['name']for e in old['emitters']};assert names==KEEP|tail
  new=copy.deepcopy(old);new['emitters']=[e for e in old['emitters']if e['name']in KEEP];assert len(new['emitters'])==6
  out=PACK/rel;compact(out,new);inputs[src.relative_to(W).as_posix()]=sha(src);outputs[out.relative_to(W).as_posix()]=sha(out)
  records.append({'profile_asset':name,'kept_original_emitters':[e['name']for e in new['emitters']],'removed_original_emitters':[e['name']for e in old['emitters']if e['name']in tail],'kept_source_parameters_unchanged':True,'kept_particles':sum(len(e['particles'])for e in new['emitters']),'no_new_geometry_or_texture':True})
 for tier in [1,2]:
  gun='hand_of_zeuss'+str(tier)
  wav=ROOT/f'宙斯、聚能/宙斯之手/音效/{gun}/WAV/{gun}_fire.wav';inputs[str(wav)]=sha(wav)
  for event in ['shoot','shoot_3p']:
   out=PACK/f'{A}/tacz_sounds/{gun}/{event}.ogg';out.parent.mkdir(parents=True,exist_ok=True)
   subprocess.run(['ffmpeg','-nostdin','-y','-v','error','-i',str(wav),'-map_metadata','-1','-c:a','libvorbis','-q:a','5',str(out)],check=True);outputs[out.relative_to(W).as_posix()]=sha(out)
  for skin in ['','_lava','_galaxy']:
   rel=f'{A}/display/guns/{gun}{skin}_display.json';src=BASE/rel;doc=read(src);doc['sounds']['shoot']=f'ssjj_zeus:{gun}/shoot';doc['sounds']['shoot_3p']=f'ssjj_zeus:{gun}/shoot_3p';out=PACK/rel;save(out,doc);inputs[src.relative_to(W).as_posix()]=sha(src);outputs[out.relative_to(W).as_posix()]=sha(out)
 # Same full original fire conversion for autonomous phantom fire; no slicing,
 # gain adjustment, resampling, filtering, synthesis or mix is applied.
 rel=f'{A}/sounds/advent/hand_of_zeuss2_fire.ogg';src=PACK/f'{A}/tacz_sounds/hand_of_zeuss2/shoot.ogg';out=PACK/rel;out.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(src,out);outputs[out.relative_to(W).as_posix()]=sha(out)
 src=BASE/f'{A}/sounds.json';events=read(src);events['advent/hand_of_zeuss2_fire']={'sounds':[{'name':'ssjj_zeus:advent/hand_of_zeuss2_fire','stream':False}]};out=PACK/f'{A}/sounds.json';save(out,events);inputs[src.relative_to(W).as_posix()]=sha(src);outputs[out.relative_to(W).as_posix()]=sha(out)
 evidence=read(W/'evidence/audio_video_matches.json');fire=next(v for v in evidence['videos']if'开火'in v['video']);summon=next(v for v in evidence['videos']if'召唤'in v['video']);assert fire['ranked_matches'][0]['normalized_waveform_correlation']>.99 and summon['ranked_matches'][0]['normalized_waveform_correlation']>.8
 save(W/'evidence/advent_fx_prepare.json',{'passed':True,'tail_removal':records,'input_sha256':inputs,'output_sha256':outputs,'audio_mapping':{'player_fire':'original full fire.wav Vorbis-only conversion via shoot/shoot_3p routes; firing video correlation0.9975534841 at1.5345s','phantom_fire':'same full original fire.wav conversion; identical encoded bytes as player shot','V_summon':'original skill.wav; summon video correlation0.8235236134 at0.718125s','chain_release':'silent; skill.wav is proven summon audio and has been removed from the chain client route','sec_fire':'not assigned; neither provided firing nor summon video establishes this file as the needed sound','reload':'original reload.wav retained, reference correlation0.8764523368','draw':'existing supplied original raise.wav retained'},'source_audio_video_report_sha256':sha(W/'evidence/audio_video_matches.json'),'source_body_policy':'Only named source tail/forward-smear emitter entries removed; retained six emitters preserve their original LUT, mesh, textures, material flags and curves exactly. No redraw.','live_client_test':False})
 print('PASS removed3/3/4 original tail emitters; restored video-confirmed fire and reserved skill for V summon')
if __name__=='__main__':main()
