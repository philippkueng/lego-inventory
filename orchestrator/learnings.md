## Play the stream (to see it visually):
```
ffplay http://esp32-31a6d4:81/
```

## Record a short clip to verify it's working:
```
ffmpeg -i http://esp32-31a6d4:81/ -t 10 -an -y test.mp4
```

## Show stream information:
```
ffmpeg -i http://esp32-31a6d4:81/ -t 1
```
(Look at the output for format, codec, resolution, fps info)
