import re

path = '/home/premanandal1978/android/waterlily/frameworks/base/services/java/com/android/server/SystemServer.java'
with open(path, 'r') as f:
    content = f.read()

content = content.replace(
    't.traceBegin(StartFrankensteinBridgeService);',
    't.traceBegin("StartFrankensteinBridgeService");'
)

with open(path, 'w') as f:
    f.write(content)

print('Fixed')
