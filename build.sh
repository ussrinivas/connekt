#!/bin/bash
set -e

time=`date +%s`

export PACKAGE=${PACKAGE:-"fk-pf-connekt"}
export TARGET=${TARGET:-local}
export LOCAL_DIR=`pwd`

rm -rf $LOCAL_DIR/deb
mkdir -p $LOCAL_DIR/deb
bash $LOCAL_DIR/make-$PACKAGE-deb

cd $LOCAL_DIR/deb

sed -i -e "s/_VERSION_/$time/g" DEBIAN/control
echo "Version: 1.$time"

cd $LOCAL_DIR

rm -f $PACKAGE.deb
dpkg -b deb $PACKAGE.deb

rm -rf $LOCAL_DIR/deb
