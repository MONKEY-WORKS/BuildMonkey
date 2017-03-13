#!/bin/bash
rm -rf $(pwd)/build/merge-repository
${ECLIPSE_HOME}/eclipse \
   -application org.eclipse.equinox.p2.artifact.repository.mirrorApplication \
   -source file:$(pwd)/build/monkey-repository \
   -destination file:$(pwd)/build/merge-repository \
   -nosplash \
   -Dequinox.scr.waitTimeOnBlock=900000

${ECLIPSE_HOME}/eclipse \
   -application org.eclipse.equinox.p2.artifact.repository.mirrorApplication \
   -source file:$(pwd)/build/p2-repository \
   -destination file:$(pwd)/build/merge-repository \
   -nosplash \
   -Dequinox.scr.waitTimeOnBlock=900000

${ECLIPSE_HOME}/eclipse \
   -application org.eclipse.equinox.p2.metadata.repository.mirrorApplication \
   -source file:$(pwd)/build/monkey-repository \
   -destination file:$(pwd)/build/merge-repository \
   -nosplash \
    -Dequinox.scr.waitTimeOnBlock=900000

${ECLIPSE_HOME}/eclipse \
   -application org.eclipse.equinox.p2.metadata.repository.mirrorApplication \
   -source file:$(pwd)/build/p2-repository \
   -destination file:$(pwd)/build/merge-repository \
   -nosplash \
   -Dequinox.scr.waitTimeOnBlock=900000

${ECLIPSE_HOME}/eclipse -application org.eclipse.equinox.p2.publisher.ProductPublisher \
   -metadataRepository file:/home/micha/branches/github/BuildMonkey/examples/exampleApp/build/merge-repository \
   -artifactRepository file:/home/micha/branches/github/BuildMonkey/examples/exampleApp/build/merge-repository \
   -productFile /home/micha/branches/github/BuildMonkey/examples/exampleApp/MonkeyExampleE4Application/MonkeyExcampleE4Application.product \
   -executables file:/home/micha/branches/github/BuildMonkey/examples/exampleApp/build/p2-repository/features/org.eclipse.equinox.executable_3.6.300.v20161122-1740/ \
   -publishArtifacts \
   -append \
   -flavor tooling \
   -configs gtk.linux.x86_64,win32.win32.x86_64 \
   -nosplash \
    -Dequinox.scr.waitTimeOnBlock=900000

${ECLIPSE_HOME}/eclipse \
    -application org.eclipse.equinox.p2.director \
    -repository file:/home/micha/branches/github/BuildMonkey/examples/exampleApp/build/merge-repository \
    -installIU MonkeyExampleE4Application.product \
    -tag InitialState \
    -destination /home/micha/branches/github/BuildMonkey/examples/exampleApp/product/linux \
    -profile DefaultProfile \
    -profileProperties org.eclipse.update.install.features=true \
    -p2.os linux \
    -p2.ws gtk \
    -p2.arch x86_64 \
    -roaming \
    -nosplash 

${ECLIPSE_HOME}/eclipse \
    -application org.eclipse.equinox.p2.director \
    -repository file:/home/micha/branches/github/BuildMonkey/examples/exampleApp/build/merge-repository \
    -installIU MonkeyExampleE4Application.product \
    -tag InitialState \
    -destination /home/micha/branches/github/BuildMonkey/examples/exampleApp/product/win \
    -profile DefaultProfile \
    -profileProperties org.eclipse.update.install.features=true \
    -p2.os win32 \
    -p2.ws win32 \
    -p2.arch x86_64 \
    -roaming \
    -nosplash 
