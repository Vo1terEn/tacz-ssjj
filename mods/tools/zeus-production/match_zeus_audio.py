"""Read-only source-audio/video comparison; never makes production audio."""
from pathlib import Path
import json,subprocess,hashlib
import numpy as np
from scipy.signal import fftconvolve,resample_poly,butter,sosfilt
from prepare import W,ROOT,save,sha
SRC=ROOT/'宙斯、聚能/宙斯之手';RATE=16000
def decode(p):
 b=subprocess.check_output(['ffmpeg','-nostdin','-v','error','-i',str(p),'-vn','-ac','1','-ar',str(RATE),'-f','f32le','pipe:1']);a=np.frombuffer(b,dtype='<f4').astype(float)
 return sosfilt(butter(3,150,fs=RATE,btype='highpass',output='sos'),a)
def match(video,source):
 if len(source)>len(video):return None
 source=source-source.mean();v=video-video.mean();dot=fftconvolve(v,source[::-1],mode='valid');power=fftconvolve(v*v,np.ones(len(source)),mode='valid');den=np.sqrt(np.maximum(1e-20,power*np.dot(source,source)));score=np.abs(dot)/den;i=int(np.argmax(score))
 return {'normalized_waveform_correlation':float(score[i]),'start_seconds':i/RATE,'source_duration':len(source)/RATE,'video_window_power':float(power[i])}
def main():
 files=sorted((SRC/'音效').rglob('*.wav'));sounds={p:decode(p)for p in files};videos=sorted(SRC.glob('*.mp4'));reports=[]
 for video in videos:
  v=decode(video);rows=[]
  for p,s in sounds.items():
   m=match(v,s)
   if m:rows.append({'source':p.relative_to(ROOT).as_posix(),'source_sha256':sha(p),**m})
  rows.sort(key=lambda x:x['normalized_waveform_correlation'],reverse=True)
  reports.append({'video':video.relative_to(ROOT).as_posix(),'video_sha256':sha(video),'duration':len(v)/RATE,'ranked_matches':rows})
 save(W/'evidence/audio_video_matches.json',{'passed':True,'sample_rate_for_analysis':RATE,'method':'Original video and supplied WAV decoded to16k mono float for analysis only,150Hz highpass, amplitude-normalized waveform cross-correlation at every lag. Production files remain original or Vorbis format conversion only.','sources':len(files),'videos':reports,'input_sha256':{str(Path(__file__).relative_to(W)):sha(Path(__file__))}})
 for report in reports:print(json.dumps({'video':report['video'],'top_matches':report['ranked_matches'][:7]},ensure_ascii=True))
if __name__=='__main__':main()
