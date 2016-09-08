# rnio

Use react-native init to create project first

## In this module

./android/build.gradle

    dependencies {
        compile 'com.facebook.react:react-native:0.30.+'
    }

Version is important.

## react-native link ...

For android, it does:

1. ./android/app/build.gradle


    dependencies {
        compile project(':<npm_module_name>')


2. ./andorid/settings.gradle
 
 
    include ':<module>'
    project(':<module>').projectDir = new File(rootProject.projectDir, '../node_modules/<module>/android')

3. ./android/app/src/main/com/<appname>/MainApplication.java