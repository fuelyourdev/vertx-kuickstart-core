version=`gradle properties --no-daemon --console=plain -q | grep "^version:" | awk '{print $2}'`
if [[ $version != *SNAPSHOT ]];
then
  echo Closing and releasing $version
  gradle closeAndReleaseRepository
else
  echo No need to close and release $version
fi
