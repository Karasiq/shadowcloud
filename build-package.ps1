$env:JAVA_OPTS="-Xmx4G -Denable-tika=1 -Denable-javacv=1"
sbt 'set packageName in desktopApp in Universal := \"shadowcloud-full-\" + version.value' desktopApp/jdkPackager:packageBin
$env:JAVA_OPTS="-Xmx4G -Denable-tika=0 -Denable-javacv=0"
sbt 'set name in Universal in desktopApp := \"shadowcloud-light-\" + version.value' desktopApp/jdkPackager:packageBin
