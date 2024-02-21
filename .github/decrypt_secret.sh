#!/bin/sh

# Decrypt the file
mkdir $HOME/secrets
# 查看当前路径，发现是：/home/runner/work/my-toolbox/my-toolbox，也就是项目根目录
pwd
ls -l -a
# --batch to prevent interactive command
# --yes to assume "yes" for questions
# 所以这里路径要写相对路径了
gpg --quiet --batch --yes --decrypt --passphrase="$LARGE_SECRET_PASSPHRASE" \
--output $HOME/secrets/secring.gpg ./.github/secring.gpg.gpg
