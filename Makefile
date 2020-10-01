clean:
	lein clean
build: clean
	lein uberjar
sync-jar:
	rsync -aRv target/*-standalone.jar pismall:/opt/apps/citron/
	rsync -aRv resources/public/ pismall:/opt/apps/citron/resources/public/
	rsync -aRv citron.sh pismall:/opt/apps/citron/
deploy: build sync-jar
