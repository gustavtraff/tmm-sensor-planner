#!/usr/bin/env python3
"""Independent fixed-fixture checks of actual SDK geometry exports; no SDK import."""
import base64
import json
import math
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

UID='00000000-0000-4000-8189-000000000001'
FIELDS={'schemaVersion','deviceUid','displayIdUtf8','type','status','ownerUtf8','createdAt','updatedAt','deployedAt','commentUtf8','latitude','longitude','bearingDeg','bearingReference','imageIncluded'}

def inspect(directory):
    point_before=None
    for name,step,moved,coherent in [('before',0,False,True),('bearing',1,False,True),('point-before-payload',1,False,False),('raw-point-and-payload',2,True,True),('moved',2,True,True)]:
        raw=(directory/(name+'.cot')).read_bytes()
        assert len(raw)<65536 and b'<!DOCTYPE' not in raw and b'<!ENTITY' not in raw
        event=ET.fromstring(raw);payloads=event.findall('./detail/tmmPublication');assert len(payloads)==1
        m=payloads[0].attrib;point=event.find('point').attrib
        assert event.attrib['uid']==UID and event.attrib['type']=='b-m-p-s-p-loc'
        assert set(m)==FIELDS and m['deviceUid']==UID and m['schemaVersion']=='1'
        assert m['type']=='PIR' and m['status']=='PLANNED' and m['imageIncluded']=='false' and m['bearingReference']=='true-north'
        assert m['createdAt']=='2026-01-01T00:00:00.123456789Z' and m['updatedAt']==f'2026-01-01T00:00:{10+step:02d}.123456789Z' and m['deployedAt']==''
        for key,value in [('displayIdUtf8','TMM-GEOMETRY189'),('ownerUtf8','SYNTHETIC LAB'),('commentUtf8','Approved G1-G5 SDK measurement')]:
            assert base64.b64decode(m[key],validate=True).decode('utf-8')==value
        latitude=0.333456789012 if moved else 0.323456789012
        longitude=-0.244567891234 if moved else -0.234567891234
        assert float(m['latitude'])==latitude and float(m['longitude'])==longitude
        assert float(m['bearingDeg'])==(123.45678901234567 if step==0 else 234.56789012345678)
        actual_coherent=all(float(point[k])==math.floor(float(m[field])*1e7+0.5)/1e7 for k,field in [('lat','latitude'),('lon','longitude')])
        assert actual_coherent==coherent
        if name=='before':point_before=point
        if name=='bearing':assert point==point_before
    proof=json.loads((directory/'proof.json').read_text())
    assert proof['passed'] and proof['sameMapItemAndUuid'] and proof['removedOwnedFixtureFromLiveMap']
    return {'verifiedExports':5,'uuid':UID,'bearingOnlyPointPreserved':True,'partialPointMismatchObserved':True,'rawPointCoherentInMemory':proof['rawPointCoherentInMemory'],'canonicalPointCoherent':True,'deliveryVerified':False}

if __name__=='__main__':
    if len(sys.argv)!=2:sys.exit('Usage: python3 scripts/check-geometry-sdk-exports.py /private/capture-directory')
    try:print(json.dumps(inspect(Path(sys.argv[1])),indent=2))
    except (AssertionError,ValueError,KeyError,OSError,AttributeError,ET.ParseError):sys.exit('Geometry SDK exports failed independent validation')
