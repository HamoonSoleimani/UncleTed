SKIPUNZIP=0
ui_print "***********************************************"
ui_print "   UncleTed Priv-App & Hook Installer          "
ui_print "***********************************************"

if [ ! -f "$MODPATH/system/priv-app/UncleTed/UncleTed.apk" ]; then
    abort "! Error: UncleTed.apk missing from package."
fi

if [ ! -f "$MODPATH/system/etc/permissions/privapp-permissions-uncleted.xml" ]; then
    abort "! Error: privapp-permissions-uncleted.xml missing from package."
fi

ui_print "- Applying permissions..."
set_perm_recursive "$MODPATH/system/priv-app/UncleTed" 0 0 0755 0644
set_perm "$MODPATH/system/priv-app/UncleTed/UncleTed.apk" 0 0 0644
set_perm "$MODPATH/system/etc/permissions/privapp-permissions-uncleted.xml" 0 0 0644
ui_print "- Configured successfully."
