export VERSION=1.0.2

sbt -J-Xmx4G -J-Denable-tika=1 -J-Denable-javacv=1 desktopApp/jdkPackager:packageBin && \
  sbt -J-Xmx4G -J-Denable-tika=0 -J-Denable-javacv=0 desktopApp/universal:packageBin && \
  mv -f "./desktop-app/target/universal/shadowcloud-desktop-$VERSION.zip" "./desktop-app/target/universal/shadowcloud-$VERSION-light.zip" && \
  sbt -J-Xmx4G -J-Denable-tika=1 -J-Denable-javacv-all=1 desktopApp/universal:packageBin && \
  mv -f "./desktop-app/target/universal/shadowcloud-desktop-$VERSION.zip" "./desktop-app/target/universal/shadowcloud-$VERSION-full.zip"
