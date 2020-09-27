clean:
	lein clean
build: clean
	lein uberjar
sync-jar:
	rsync -av target/*-standalone.jar hk:/home/garfield/staged/jars/citron/
	rsync -av config-citron.edn hk:/home/garfield/staged/jars/citron/config-citron.edn
	rsync -av project.clj hk:/home/garfield/staged/jars/citron/
deploy: build sync-jar
	ansible-playbook citron-ansible.yml
sync-source:
	csync-projects.sh citron hk "/home/garfield/"
	rsync -av config-citron.edn hk:/home/garfield/projects/citron/config-citron.edn
