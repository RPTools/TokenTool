TokenTool 2.1 - _Infused with Nerps!_
=====
I'm excited to bring to you the next version of _Infused with Nerps!_ 

You can now add background images, extract images from PDF files, and save the portrait alongside the token using the same background image and/or colors!  Several bug fixes and requested enhancements have also been added. 


Enhancements
-----
* [#7][i7] - *Add PDF Extraction*. Open and extract images straight from the module! Using the File -> Open PDF menu or dragging a PDF to the main window, you can open and view and PDF. The individual images are shown to the right and you can either drag them to the main window or left-click with the mouse to use the image as the Portrait or right-click with the mouse to use the image as the background.
* [#10][i10] - *Add arrow key nudges*. You can use the arrow keys as well as the number pad to nudge the images 1 pixel at a time! Be sure you have the layer selected that you want to move.
* [#12][i12] - *Update CI config and install to Java 10*. The packaged JRE has been bumped up to Oracle JDK 10 and is now the required JRE if launching the JAR manually. 
* [#13][i13] - *Allow user supplied image for background*. In addition to background color, you can now supply a background image! When dragging images to the main window, drop zones will highlight and the "Layer" button in the upper right will tell you if you are setting the Portrait or Background image. Several options have been added to support the use of backgrounds as well.
* [#14][i14] - *Add a keyboard shortcut to take a screen capture*.  Several keyboard shortcuts as well as Accelerators have been added to the various menu items.
* [#15][i15] - *The screen capture box could retain its size*. The screen capture window now remembers its size! In fact, all the windows now remember their size and position!
* [#19][i19] - *Add ESC key to cancel Screen Capture*. Didn't mean to capture that screen shot? Pressing ESC key will now cancel the action and close the window.
* Misc - A new menu option under Help -> Reset Settings will restore all settings to their defaults.
* Misc - A few new overlays were added, enjoy!
* Misc - If you unlock the aspect ratio (pressing the padlock on the Overlay Options), you can now set the token size to any integer.
* Misc - The overlay's original width x height is now displayed under the Overlays name on the Overlay Options panel
* Misc - Save options were expanded to allow you to save the Portrait used (with or without Background color/image) alongside the token. This is useful if you grabbed the image from a URL or PDF and forgot or couldn't save it. Also, if you save with Background Options, it will save the portrait as a .jpg instead of a .png (which are typically 10x smaller in disk space) so you can use that high quality PNG from the PDF to create a token but use a smaller jpg for the Portrait (say, in MapTool) where transparency is probably not needed or wanted.


Bug Fixes
-----
* [#8][i8] - *Bug in pdf-extract-feature branch*. This has been squashed!
* [#9][i9] - *Pog filenames substitute %20 for spaces*. Token names are now properly unescaped from URL's. No more %20's!
* [#11][i11] - *Background color layer not on the bottom*. Another bug squashed!
* [#17][i17] - *Imported PNG overlays coming in at 100x100*. Squashed this one too!


[i7]: https://github.com/JamzTheMan/TokenTool/issues/7
[i8]: https://github.com/JamzTheMan/TokenTool/issues/8
[i9]: https://github.com/JamzTheMan/TokenTool/issues/9
[i10]: https://github.com/JamzTheMan/TokenTool/issues/10
[i11]: https://github.com/JamzTheMan/TokenTool/issues/11
[i12]: https://github.com/JamzTheMan/TokenTool/issues/12
[i13]: https://github.com/JamzTheMan/TokenTool/issues/13
[i14]: https://github.com/JamzTheMan/TokenTool/issues/14
[i15]: https://github.com/JamzTheMan/TokenTool/issues/15
[i17]: https://github.com/JamzTheMan/TokenTool/issues/17
[i19]: https://github.com/JamzTheMan/TokenTool/issues/19