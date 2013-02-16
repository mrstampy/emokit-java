emokit-java
===========

Open Source Java driver to access raw data from the [Emotiv EPOC EEG](http://www.emotiv.com) headset.

[![Zoku Screenshot](http://i49.tinypic.com/295s5xy.jpg)](http://youtu.be/Ve7MEuuzXuY)

*(click image for a video of the demonstration data acquisition application)*

Demo
====

Several Java Swing GUI widgets are provided for use in your applications, as demonstrated in the
bundled *Zoku* data acquisition application. To run Zoku, you must have a PostgreSQL server
running on `localhost` with a database called `zoku` (this sounds like a pain, but it makes
integration with [R](http://cran.r-project.org) much easier).

* run postgresql on localhost, username `postgres`
* create a database called `zoku`
* `mvn compile`
* `mvn exec:java`

Raw access is through the `com.github.fommil.emokit.Emotiv` class:

```java
Emotiv emotiv = new Emotiv();
for (Packet packet : emotiv) {
    ...
}
```

the special `Iterator` will continue until the EEG device is disconnected, or there are IO problems.
If that happens, a new `Emotiv` instance can be obtained and polled.

Installation
============

Not currently being distributed via Maven Central,
[but it will be](https://github.com/fommil/emokit-java/issues/2).

If you wish to build on this library, clone this github repository
and install locally using Maven (`mvn install`).


Donations
=========

Please consider supporting the maintenance of this open source project with a donation:

[![Donate via Paypal](https://www.paypal.com/en_US/i/btn/btn_donateCC_LG.gif)](https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=B2HW5ATB8C3QW&lc=GB&item_name=emokit-java&currency_code=GBP&bn=PP%2dDonationsBF%3abtn_donateCC_LG%2egif%3aNonHosted)


Licence
=======

Copyright (C) 2012 Samuel Halliday

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see http://www.gnu.org/licenses/


Contributing
============

Contributors are encouraged to fork this repository and issue pull
requests. Contributors implicitly agree to assign an unrestricted licence
to Sam Halliday, but retain the copyright of their code (this means
we both have the freedom to update the licence for those contributions).

History
=======

This is a port and extension of [emokit](https://github.com/openyou/emokit).

Thanks go to the following for reverse engineering, original code and assistance:

* [Cody Brocious](http://github.com/daeken)
* [Kyle Machulis](http://github.com/qdot)
* [Bill Schumacher](http://github.com/bschumacher)

