Contributing to Memory Analyzer 
====================

Thanks for your interest in the Memory Analyzer project!

Project description:
--------------------

The Eclipse Memory Analyzer is a fast and feature-rich Java heap dump analyzer that helps you find memory leaks and reduce memory consumption.

- Memory Analyzer web site: <http://eclipse.dev/mat/>

Source
------

The Memory Analyzer code is hosted on Github.

<https://github.com/eclipse-mat/mat>

Developer resources
--------------------

Information regarding setting up your development environment, building MAT, running tests, coding standards, and more.

- [Contributor Reference](dev-doc/Contributor_Reference.md)
- [Developers](https://www.eclipse.org/mat/developers/) area on the MAT web site

Contributing a change
--------------------

- The section "Eclipse Contributor Agreement" below describes the formal requirements for contributing to an Eclipse project
- [Fork the MAT repository on GitHub](https://github.com/eclipse-mat/mat/fork)
- Clone the forked repository onto your computer: ``` git clone https://github.com/<your username>/mat.git ``` 
  and checkout the ```master``` branch
- See [MemoryAnalyzer/Contributor Reference](dev-doc/Contributor_Reference.md) for instructions on setting up a local development environment for MAT
- Make your changes
- Ensure that all new and existing tests pass.
- Commit the changes into the branch: ``` git commit -s ``` Make sure that
   your commit message is meaningful and describes your changes correctly.
- If you have a lot of commits for the change, squash them into a single / few
   commits.
- Push the changes in your branch to your forked repository.
- Consider opening an issue on the MAT project to discuss your contribution
- Finally, go to
	[https://github.com/eclipse-mat/mat](https://github.com/eclipse-mat/mat)
	and create a pull request from your "YOUR_BRANCH_NAME" branch to the
	```master``` branch to request review.

What happens next depends on the content of the patch. If it is 100% authored
by the contributor and is less than 1000 lines (and meets the needs of the
project), then it can be pulled into the main repository. If not, more steps
are required. These are detailed in the
[legal process poster](http://www.eclipse.org/legal/EclipseLegalProcessPoster.pdf).

Eclipse Contributor Agreement:
------------------------------

In order for your contribution to be accepted, it must comply with the Eclipse Foundation IP policy.
- You need to electronically sign the [Eclipse Contributor Agreement](https://www.eclipse.org/legal/ECA.php) (ECA). Find more information in the ECA [FAQ](https://www.eclipse.org/legal/ecafaq.php).
  - Register for an Eclipse Foundation User ID. You can register [here](https://accounts.eclipse.org/user/register).
  - Log into the [Accounts Portal](https://accounts.eclipse.org/), and click on the '[Eclipse Contributor Agreement](https://accounts.eclipse.org/user/eca)' link.
- Go to your [account settings](https://accounts.eclipse.org/user/edit) and add your GitHub username to your account.
- Make sure that you _sign-off_ your Git commits in the following format:
  ``` Signed-off-by: John Smith <johnsmith@nowhere.com> ``` This is usually at the bottom of the commit message. You can automate this by adding the '-s' flag when you make the commits. e.g.   ```git commit -s -m "Adding a cool feature"```
- Ensure that the email address that you make your commits with is the same one you used to sign up to the Eclipse Foundation website with.

For more details see [Eclipse Foundation policy on accepting contributions via Git](http://wiki.eclipse.org/Development_Resources/Contributing_via_Git).

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
