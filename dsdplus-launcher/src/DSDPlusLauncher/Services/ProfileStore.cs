using System;
using System.Collections.Generic;
using System.IO;
using System.Text.Json;
using DSDPlusLauncher.Models;

namespace DSDPlusLauncher.Services
{
    /// <summary>
    /// Lit/ecrit la liste des profils dans %AppData%\DSDPlusLauncher\profiles.json
    /// </summary>
    public class ProfileStore
    {
        private readonly string _filePath;

        public ProfileStore()
        {
            var dir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                "DSDPlusLauncher");
            Directory.CreateDirectory(dir);
            _filePath = Path.Combine(dir, "profiles.json");
        }

        public string ConfigDirectory => Path.GetDirectoryName(_filePath) ?? string.Empty;

        public List<LauncherProfile> Load()
        {
            if (!File.Exists(_filePath))
            {
                return new List<LauncherProfile>();
            }

            try
            {
                var json = File.ReadAllText(_filePath);
                var profiles = JsonSerializer.Deserialize<List<LauncherProfile>>(json);
                return profiles ?? new List<LauncherProfile>();
            }
            catch
            {
                return new List<LauncherProfile>();
            }
        }

        public void Save(IEnumerable<LauncherProfile> profiles)
        {
            var options = new JsonSerializerOptions { WriteIndented = true };
            var json = JsonSerializer.Serialize(profiles, options);
            File.WriteAllText(_filePath, json);
        }
    }
}
