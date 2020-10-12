clean:
	lein clean
build: clean
	lein uberjar
sync-jar:
	rsync -av target/*-standalone.jar pismall:/opt/apps/citron/citron.jar
	rsync -av resources/public/ pismall:/opt/apps/citron/resources/public/
	rsync -av citron.sh pismall:/opt/apps/citron/
deploy: build sync-jar
