# ThemeChangeAnimationContainer
##### A useful android compose animation scope, provides telegram/coolapk style theme change animation.
You can use it as this example:
```
// "this" is your activity instance
// it provides a scope, you can use "startThemeChangeAnime" to start animation
// warning: you should use " startThemeChangeAnime" in click event!
// ThemeChangeAnimationContainer will fill max size
// you can use "isAnimating" to get theme change animation is running, and set the startRadius to change the initial value of animation radius in the scope
ThemeChangeAnimationContainer(this) {
    Box(Modifier.clickable {
        startThemeChangeAnime {
            // do theme change
            isDarkMode.value = true
        }
    })
}
```
