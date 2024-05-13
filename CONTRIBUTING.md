Contributing to Memory Analyzer 
====================

Thanks for your interest in this project.

Project description:
--------------------

The Eclipse Memory Analyzer is a fast and feature-rich Java heap dump analyzer that helps you find memory leaks and reduce memory consumption.

- <http://eclipse.dev/mat/>

Source
------

The Memory Analyzer code is stored in a git repository on Github.

<https://github.com/eclipse-mat/mat>

You can contribute bugfixes and new features by sending pull requests through GitHub.

Developer resources:
--------------------

Information regarding source code management, builds, coding standards, and more.

- [Developers](https://www.eclipse.org/mat/developers/) area on the MAT web site
- [MemoryAnalyzer/Contributor Reference](http://wiki.eclipse.org/index.php?title=MemoryAnalyzer/Contributor_Reference) Wiki pages


Contributing a patch
--------------------

## Legal

In order for your contribution to be accepted, it must comply with the Eclipse
Foundation IP policy.

Please read the [Eclipse Foundation policy on accepting contributions via Git](http://wiki.eclipse.org/Development_Resources/Contributing_via_Git).

1. Sign the [Eclipse ECA](http://www.eclipse.org/legal/ECA.php)
    1. Register for an Eclipse Foundation User ID. You can register [here](https://accounts.eclipse.org/user/register).
    2. Log into the [Accounts Portal](https://accounts.eclipse.org/), and click on the '[Eclipse Contributor Agreement](https://accounts.eclipse.org/user/eca)' link.
2. Go to your [account settings](https://accounts.eclipse.org/user/edit) and add your GitHub username to your account.
3. Make sure that you _sign-off_ your Git commits in the following format:
  ``` Signed-off-by: John Smith <johnsmith@nowhere.com> ``` This is usually at the bottom of the commit message. You can automate this by adding the '-s' flag when you make the commits. e.g.   ```git commit -s -m "Adding a cool feature"```
4. Ensure that the email address that you make your commits with is the same one you used to sign up to the Eclipse Foundation website with.

## Contributing a change

1. [Fork the repository on GitHub](https://github.com/eclipse-mat/mat/fork)
2. Clone the forked repository onto your computer: ``` git clone
   https://github.com/<your username>/mat.git ```
3. If you are adding a new feature, then create a new branch from the latest
   ```develop``` branch with ```git checkout -b YOUR_BRANCH_NAME
   origin/develop```
4. If you are fixing a bug, then create a new branch from the latest
   ```fixes``` branch with ```git checkout -b YOUR_BRANCH_NAME origin/fixes```
5. Make your changes
6. Ensure that all new and existing tests pass.
7. Commit the changes into the branch: ``` git commit -s ``` Make sure that
   your commit message is meaningful and describes your changes correctly.
8. If you have a lot of commits for the change, squash them into a single / few
   commits.
9. Push the changes in your branch to your forked repository.
10. Finally, go to
	[https://github.com/eclipse-mat/mat](https://github.com/eclipse-mat/mat)
	and create a pull request from your "YOUR_BRANCH_NAME" branch to the
	```develop``` or ```fixes``` branch as appropriate to request review and
	merge of the commits in your pushed branch.

What happens next depends on the content of the patch. If it is 100% authored
by the contributor and is less than 1000 lines (and meets the needs of the
project), then it can be pulled into the main repository. If not, more steps
are required. These are detailed in the
[legal process poster](http://www.eclipse.org/legal/EclipseLegalProcessPoster.pdf).

Eclipse Contributor Agreement:
------------------------------

In order to contribute to the project you need to electronically sign the [Eclipse Contributor Agreement](https://www.eclipse.org/legal/ECA.php) (ECA). Find more information in the ECA [FAQ](https://www.eclipse.org/legal/ecafaq.php).

- <https://www.eclipse.org/legal/ECA.php>

Contact:
--------

Contact the project developers via the project "dev" list.

- <https://dev.eclipse.org/mailman/listinfo/mat-dev>

Search for bugs:
----------------

This project uses Bugzilla to track ongoing development and issues.

- <https://github.com/eclipse-mat/mat/issues?q=is%3Aopen+is%3Aissue>

Create a new bug:
-----------------

Be sure to search for existing bugs before you create another one. Remember that contributions are always welcome!

- <https://github.com/eclipse-mat/mat/issues/new>
