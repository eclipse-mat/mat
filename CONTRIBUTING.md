Contributing to Memory Analyzer 
====================

Thanks for your interest in this project.

Project description:
--------------------

The Eclipse Memory Analyzer is a fast and feature-rich Java heap dump analyzer that helps you find memory leaks and reduce memory consumption.

- http://eclipse.org/mat/

Source
------

The Memory Analyzer code is stored in a git repository. The URLs to access it are:

ssh://<username>@git.eclipse.org/gitroot/www.eclipse.org/mat.git

A web browsable repository is available at

http://git.eclipse.org/c/mat/org.eclipse.mat.git/

Contributing a patch
--------------------

The MAT repositories are accessed through Gerrit, the code review
project, which makes it possible for anybody to clone the repository, make
changes and push them back for review and eventual acceptance into the project.

To do this, you must follow a few steps. The first of these are described at

- https://wiki.eclipse.org/Development_Resources/Contributing_via_Git

* Sign the Eclipse CLA
* Use a valid commit record, including a signed-off-by entry.

There are further details at

- https://wiki.eclipse.org/Development_Resources/Handling_Git_Contributions

Once the patch is pushed back to Gerrit, the project committers will be
informed and they will undertake a review of the code. The patch may need
modifying for some reason. In order to make amending commits more
straightforward, the steps at
https://git.eclipse.org/r/Documentation/cmd-hook-commit-msg.html should be
followed. This automatically inserts a "Change-Id" entry to your commit message
which allows you to amend commits and have Gerrit track them as the same
change.

What happens next depends on the content of the patch. If it is 100% authored
by the contributor and is less than 250 lines (and meets the needs of the
project), then it can be committed to the main repository. If not, more steps
are required. These are detailed in the legal process poster:

- http://www.eclipse.org/legal/EclipseLegalProcessPoster.pdf

Developer resources:
--------------------

Information regarding source code management, builds, coding standards, and more.

- http://wiki.eclipse.org/index.php?title=MemoryAnalyzer/Contributor_Reference

Contributor License Agreement:
------------------------------

Before your contribution can be accepted by the project, you need to create and electronically sign the Eclipse Foundation Contributor License Agreement (CLA).

- http://www.eclipse.org/legal/CLA.php

Contact:
--------

Contact the project developers via the project "dev" list.

- https://dev.eclipse.org/mailman/listinfo/mat-dev

Search for bugs:
----------------

This project uses Bugzilla to track ongoing development and issues.

- https://bugs.eclipse.org/bugs/buglist.cgi?product=MAT

Create a new bug:
-----------------

Be sure to search for existing bugs before you create another one. Remember that contributions are always welcome!

- https://bugs.eclipse.org/bugs/enter_bug.cgi?product=MAT
