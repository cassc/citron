# citron

A file server written in Clojure

# To start

```bash
CITRON_FILE_ROOT=/var/www/public \
CITRON_MAX_PREVIEW_SIZE=1024  \ #size in kb
CITRON_LOG_FILE=citron.log
CITRON_LOG_LEVEL=info \
CITRON_ip=0.0.0.0 \
CITRON_PORT=9090 \
lein run 
```


Options are set through environmental variables, the following are supported:

* `SESSION_TTL` 
* `ROOT`
* `LOG_LEVEL`
* `LOG_FILE`
* `PORT`
* `IP`


# API

* Login

```bash
curl  -v \
-H 'Content-Type: application/json' \
-X POST \
-d '{
"username" : "hello",
"password":"hello"
}' \
http://localhost:9090/pub/login
```

* Get file info / file list in directory

```bash
curl \
-H 'Cookie: ring-session=151214e5-a6bd-4fb9-bcb3-bd00ce110b1f;Path=/;HttpOnly' \
http://localhost:9090/file?path=.
```

* 
