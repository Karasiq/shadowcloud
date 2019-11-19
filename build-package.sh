export VERSION=1.0.2

export "SBT_OPTS=-Xmx8G -Denable-tika=0 -Denable-javacv=0" && \
  sbt desktopApp/universal:packageBin && mv -f "./desktop-app/target/universal/shadowcloud-desktop-$VERSION.zip" "./desktop-app/target/universal/shadowcloud-$VERSION-light.zip" && \
  export SBT_OPTS=-"Xmx8G -Denable-tika=1 -Denable-javacv=1" && sbt desktopApp/jdkPackager:packageBin && export SBT_OPTS="-Xmx8G -Denable-tika=1 -Denable-javacv-all=1" && \
  sbt desktopApp/universal:packageBin && mv -f "./desktop-app/target/universal/shadowcloud-desktop-$VERSION.zip" "./desktop-app/target/universal/shadowcloud-$VERSION-full.zip"
