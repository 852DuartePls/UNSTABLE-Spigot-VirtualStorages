### VirtualStorages Plugin

**Description:**  
VirtualStorages is a Bukkit plugin designed to enhance storage capabilities for players on Minecraft servers. It introduces the concept of virtual backpacks, allowing players to store items beyond their regular inventory limits.

---

**Features:**

1. **Virtual Backpacks:** Players can access additional storage space through virtual backpacks.
2. **Permission System:** Granular permissions control who can use virtual backpacks, ensuring balanced gameplay.
3. **Reload Command:** Admins can reload permissions on-the-fly using the `vsreload` command.
4. **Data Persistence:** Player backpack data is saved and loaded automatically, even across server restarts. The data is stored in a YAML file located inside the Data Folder of the plugin. This ensures that player inventories remain accessible across sessions and also provides easy editing of this file.
---

**Installation:**

1. Download the latest version of VirtualStorages from the releases page.
2. Place the plugin JAR file in your server's `plugins` folder.
3. Start or restart your Minecraft server.

---

**Commands:**

- **/backpack:** Opens the virtual backpack for players who have the appropriate permission.
- **/vsreload:** Reloads permissions for VirtualStorages. Requires admin privileges.

---

**Permissions:**

- **virtualstorages.use.#:** Allows access to virtual backpacks, where "#" is the backpack number (1-999).
- **virtualstorages.admin.reload:** Allows admins to reload VirtualStorages permissions.

---

**Credits:**  
- Developer: DaveDuart
- Spigot Page: [852Duarte]([https://dev.bukkit.org](https://www.spigotmc.org/members/852duarte.637824/))

---

**Support:**  
For support and updates, join our Discord server [here](https://discord.gg/virtualstorages).

---

**License:**  
VirtualStorages is licensed under the GNU General Public License v3.0. See the `LICENSE` file for details.

---

**Feedback:**  
Your feedback is valuable! Feel free to submit bug reports, feature requests, or general feedback on the GitHub repository's issues section.

---
