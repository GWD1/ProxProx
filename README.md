![ProxProx](.github/ASSETS/logo_optimized.png)

<h4 align="center">A new fresh Minecraft Bedrock Edition proxy<br>aiming for stability and performance</h4>
<p align="center">
  
  <!-- STAR BADGE -->
  <a href="https://github.com/GoMint/ProxProx/stargazers">
    <img alt="GitHub Stars" src="https://img.shields.io/github/stars/GoMint/ProxProx.svg">
  </a>
  <!-- ISSUES BADGE -->
  <a href="https://github.com/GoMint/ProxProx/issues">
    <img alt="GitHub Issues" src="https://img.shields.io/github/issues/GoMint/ProxProx.svg">
  </a>
  <!-- VERSION BADGE -->
  <a href="https://github.com/GoMint/ProxProx">
    <img alt="Version" src="https://img.shields.io/badge/version-0.0.1-green.svg">
  </a>
  <!-- LICENSE BADGE -->
  <a href="https://opensource.org/licenses/BSD-3-Clause">
    <img alt="License" src="https://img.shields.io/badge/License-BSD%203--Clause-blue.svg">
  </a>

</p>

ProxProx is an open-source proxy for Minecraft Bedrock Edition servers. It allows creating plugins and moving players between all sorts of PE servers without the need of third party plugins. Purely written in Java.

### A word of warning
Currently, ProxProx is in a fast development-mode. The API is not that stable and may change over time. The goal is to develop an API with the implementation problems we face. We will eventually break the API multiple times until we reach the first release. To keep the impact minimal we deprecate symbols and provide better alternatives you can use.

```diff
- Deprecated symbols (packages, fields, methods, classes etc.) will be deleted after two weeks of deprecation
```

## tl;dr
| JDK  | Documentation | Download                                                    |
| ---- | ------------- | ----------------------------------------------------------- |
| 1.8  | Not available | [Latest build](https://travis-ci.org/GoMint/ProxProx) |

### Compilation
Compiling ProxProx is actually pretty easy. We'll guide you through the compilation step by step and address troubleshooting.

**Prerequisites**<br>
For compiling ProxProx, you will need some prerequisites:
- Git
- Maven
- JDK 1.8
 
**Compiling**<br>
This project's choice of build tool is Maven. To compile ProxProx using Maven follow these steps:
- Open up a terminal
- Change the working directory to the cloned ProxProx repository
- Type the following command: `mvn clean install` (You can append `-T 4C` if you've got a decent machine)
- Let it compile. This will take some time. Grab a drink and relax.

**Troubleshooting**<br>
_No compile troubleshooting available._

### License
This project's choice of license is **BSD 3-Clause**. You may find the license file in the project's root directory.

<br>
<p align="center">
  
  <!-- DISCORD -->
  <a href="https://discord.gg/qC4nJVN">
    <img width="32" alt="Discord Logo" src=".github/ASSETS/logo_discord.png">
  </a>
  &nbsp;
  <!-- TWITTER -->
  <a href="https://twitter.com/GomintPe">
    <img width="32" alt="Twitter Logo" src=".github/ASSETS/logo_twitter.png">
  </a>

</p>
