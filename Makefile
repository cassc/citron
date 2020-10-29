clean:
	lein clean
web:
	cd ../citron-web/ ; make build
build: clean web
	lein uberjar
