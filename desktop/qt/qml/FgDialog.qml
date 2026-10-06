import QtQuick
import QtQuick.Controls.Basic
Dialog {
 id: control
 Theme {id: dialogTheme}
 palette.window: dialogTheme.panel
 palette.windowText: dialogTheme.white
 palette.text: dialogTheme.white
 palette.base: dialogTheme.navy
 palette.button: dialogTheme.raised
 palette.buttonText: dialogTheme.white
 palette.highlight: dialogTheme.mint
 background: Rectangle {color:dialogTheme.panel;radius:14;border.width:1;border.color:dialogTheme.muted}
}
