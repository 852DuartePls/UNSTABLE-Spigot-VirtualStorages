### VirtualStorages Plugin

**Description:**  
VirtualStorages is a Minecraft Plugin designed to increase storage capabilities for players on servers. It introduces virtual backpacks, allowing players to store items inside a virtual compartment that is accessible from everywhere and anywhere.

---

**Features:**

1. **Virtual Backpacks:** Players can access additional storage slots inside a virtual backpack.
2. **Permission:** Permissions to control who can use virtual backpacks and to manipulate the number of backpacks a player can have.
3. **Admin Control:** Admins can inspect and manipulate the contents of every backpack created by players using `/backpackview <player>`.
4. **Data Persistence:** The data is stored in a YAML file located inside the Data Folder of the plugin.
---

**Installation:**

1. Download the latest version of VirtualStorages from the releases page.
2. Place the plugin JAR file in your server's `plugins` folder.
3. Start or restart your Minecraft server.

---

**Commands:**

- **/backpack:** Opens the virtual backpack for players who have the appropriate permission.
- **/backpackview <player>:** Allows the player to visualize and interact with another player's backpack.
- **/vsreload:** Reloads permissions for VirtualStorages. Requires admin privileges.

---

**Permissions:**

- **virtualstorages.use.#:** Allows access to virtual backpacks, where "#" is the backpack number (1-999).
- **virtualstorages.admin:** Allows admins to access the `/vsreload` and `/backpackview` commands.

---

**Credits:**  
- Developer: DaveDuart
- Spigot Page: [852Duarte](https://www.spigotmc.org/members/852duarte.637824/)

---

**Support:**  
For support add me on discord, username: 852Duarte.

---

**License:**  
VirtualStorages is licensed under the GNU General Public License v3.0. See the `LICENSE` file for details.

---

**Feedback:**  
Your feedback is valuable! Feel free to submit bug reports, feature requests, or general feedback on the GitHub repository's issues section.

---

#### **TODO:**
- [x] Add a way to see other players' backpacks in-game
- [ ] Make `/vsreload` no longer necessary
- [ ] Add option for Database storage

--- 
