# CI/Makefile only. Agents and humans at a REPL use kaocha.repl instead (docs/ai/running-tests.md).
# One JVM per CLJ suite: loading the demo breaks :unit rendering tests in the same JVM (until rp-02).
tests:
	pnpm install --frozen-lockfile
	pnpm exec shadow-cljs compile ci-tests
	pnpm exec karma start --single-run
	clojure -M:dev:tests:demo:clj-tests :unit
	clojure -M:dev:tests:demo:clj-tests :demo

docs/DevelopersGuide.html: docs/DevelopersGuide.adoc
	asciidoctor -o docs/DevelopersGuide.html -b html5 -r asciidoctor-diagram docs/DevelopersGuide.adoc

book: docs/DevelopersGuide.html

publish: book
	rsync -av docs/DevelopersGuide.html linode:/usr/share/nginx/html/RAD.html
	rsync -av docs/assets linode:/usr/share/nginx/html/

deploy:
	rm -rf target
	mvn deploy
