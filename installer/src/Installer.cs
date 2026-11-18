using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Text;
using System.Threading;
using System.Windows.Forms;
using Microsoft.Win32;

namespace LostFilmSetup
{
    internal static class Program
    {
        [STAThread]
        private static int Main(string[] args)
        {
#if UNINSTALLER
            return Uninstaller.Run();
#else
            return InstallerApp.Run(args);
#endif
        }
    }

    internal static class Theme
    {
        public static readonly Color Text = Color.FromArgb(242, 245, 250);
        public static readonly Color Muted = Color.FromArgb(147, 160, 180);
        public static readonly Color AccentA = Color.FromArgb(79, 195, 247);
        public static readonly Color AccentB = Color.FromArgb(124, 92, 255);
        public static readonly Color Dark = Color.FromArgb(11, 13, 19);
    }

    internal static class Draw
    {
        public static GraphicsPath Round(RectangleF r, float radius)
        {
            GraphicsPath path = new GraphicsPath();
            float d = radius * 2f;
            if (d > r.Width) d = r.Width;
            if (d > r.Height) d = r.Height;
            path.AddArc(r.X, r.Y, d, d, 180, 90);
            path.AddArc(r.Right - d, r.Y, d, d, 270, 90);
            path.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
            path.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
            path.CloseFigure();
            return path;
        }

        public static void FillRound(Graphics g, Color color, RectangleF r, float radius)
        {
            using (GraphicsPath path = Round(r, radius))
            using (SolidBrush brush = new SolidBrush(color))
            {
                g.FillPath(brush, path);
            }
        }

        public static void Glow(Graphics g, RectangleF r, Color color, int alpha)
        {
            using (GraphicsPath path = new GraphicsPath())
            {
                path.AddEllipse(r);
                using (PathGradientBrush brush = new PathGradientBrush(path))
                {
                    brush.CenterColor = Color.FromArgb(alpha, color);
                    Color[] surround = new Color[path.PointCount];
                    for (int i = 0; i < surround.Length; i++) surround[i] = Color.FromArgb(0, color);
                    brush.SurroundColors = surround;
                    g.FillPath(brush, path);
                }
            }
        }

        public static void GlowRing(Graphics g, RectangleF r, float radius, Color color, int alpha, float width)
        {
            for (int i = 0; i < 3; i++)
            {
                float inflate = 2f + i * 3f;
                RectangleF rr = RectangleF.Inflate(r, inflate, inflate);
                using (GraphicsPath path = Round(rr, radius + inflate))
                using (Pen pen = new Pen(Color.FromArgb(alpha / (i + 1), color), width + i * 2f))
                {
                    g.DrawPath(pen, path);
                }
            }
        }

        public static void Text(Graphics g, string text, Font font, Color color, float x, float y)
        {
            using (SolidBrush brush = new SolidBrush(color))
            {
                g.DrawString(text, font, brush, x, y);
            }
        }

        public static void TextCenter(Graphics g, string text, Font font, Color color, RectangleF rect)
        {
            using (StringFormat format = new StringFormat())
            {
                format.Alignment = StringAlignment.Center;
                format.LineAlignment = StringAlignment.Center;
                using (SolidBrush brush = new SolidBrush(color))
                {
                    g.DrawString(text, font, brush, rect, format);
                }
            }
        }

        public static void TextWrapped(Graphics g, string text, Font font, Color color, RectangleF rect)
        {
            using (StringFormat format = new StringFormat())
            {
                format.Trimming = StringTrimming.EllipsisWord;
                format.FormatFlags = StringFormatFlags.LineLimit;
                using (SolidBrush brush = new SolidBrush(color))
                {
                    g.DrawString(text, font, brush, rect, format);
                }
            }
        }

        public static float Measure(Graphics g, string text, Font font)
        {
            return g.MeasureString(text, font).Width;
        }

        public static void GradientRound(Graphics g, RectangleF r, float radius)
        {
            using (GraphicsPath path = Round(r, radius))
            using (LinearGradientBrush brush = new LinearGradientBrush(r, Theme.AccentA, Theme.AccentB, 45f))
            {
                g.FillPath(brush, path);
            }
        }

        public static void CheckMark(Graphics g, RectangleF r, Color color, float width)
        {
            using (Pen pen = new Pen(color, width))
            {
                pen.StartCap = LineCap.Round;
                pen.EndCap = LineCap.Round;
                pen.LineJoin = LineJoin.Round;
                g.DrawLines(pen, new PointF[]
                {
                    new PointF(r.X + r.Width * 0.15f, r.Y + r.Height * 0.55f),
                    new PointF(r.X + r.Width * 0.42f, r.Y + r.Height * 0.80f),
                    new PointF(r.X + r.Width * 0.85f, r.Y + r.Height * 0.22f)
                });
            }
        }

        public static void PlayTriangle(Graphics g, RectangleF r, Color color)
        {
            using (SolidBrush brush = new SolidBrush(color))
            {
                g.FillPolygon(brush, new PointF[]
                {
                    new PointF(r.X + r.Width * 0.36f, r.Y + r.Height * 0.28f),
                    new PointF(r.X + r.Width * 0.74f, r.Y + r.Height * 0.5f),
                    new PointF(r.X + r.Width * 0.36f, r.Y + r.Height * 0.72f)
                });
            }
        }
    }

    internal static class AppArt
    {
        private static bool _loaded;
        private static Icon _icon;
        private static Bitmap _bitmap;

        public static Icon Icon
        {
            get { Load(); return _icon; }
        }

        public static Bitmap Bitmap
        {
            get { Load(); return _bitmap; }
        }

        private static void Load()
        {
            if (_loaded) return;
            _loaded = true;
            try
            {
                using (Stream stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("LostFilmInstaller.app.ico"))
                {
                    if (stream == null) return;
                    using (Icon icon = new Icon(stream, new Size(128, 128)))
                    {
                        _icon = (Icon)icon.Clone();
                        _bitmap = icon.ToBitmap();
                    }
                }
            }
            catch
            {
            }
        }
    }

    internal sealed class Hit
    {
        public Rectangle Bounds;
        public string Id;

        public Hit(Rectangle bounds, string id)
        {
            Bounds = bounds;
            Id = id;
        }
    }

    internal sealed class PayloadArchive : IDisposable
    {
        private FileStream _stream;
        private ZipArchive _zip;
        private string _temp;

        private PayloadArchive(FileStream stream, ZipArchive zip, string temp)
        {
            _stream = stream;
            _zip = zip;
            _temp = temp;
        }

        public static PayloadArchive Open()
        {
            string self = Application.ExecutablePath;
            long length;
            using (FileStream fs = new FileStream(self, FileMode.Open, FileAccess.Read, FileShare.Read))
            {
                if (fs.Length < 32) throw new InvalidOperationException("Payload не найден: файл слишком мал.");
                fs.Seek(-8, SeekOrigin.End);
                byte[] lenBytes = new byte[8];
                ReadExactly(fs, lenBytes);
                length = BitConverter.ToInt64(lenBytes, 0);
                if (length <= 0 || length > fs.Length - 32) throw new InvalidOperationException("Payload повреждён: неверная длина.");

                fs.Seek(-24, SeekOrigin.End);
                byte[] magic = new byte[16];
                ReadExactly(fs, magic);
                string marker = Encoding.ASCII.GetString(magic).TrimEnd('\0');
                if (marker != "ADBWIFISETUP0001") throw new InvalidOperationException("Payload не найден: неверная подпись.");

                long start = fs.Length - 24 - length;
                string temp = Path.Combine(Path.GetTempPath(), "LostFilmPayload_" + Guid.NewGuid().ToString("N") + ".zip");
                using (FileStream output = new FileStream(temp, FileMode.Create, FileAccess.Write, FileShare.None))
                {
                    fs.Seek(start, SeekOrigin.Begin);
                    byte[] buffer = new byte[1024 * 1024];
                    long remaining = length;
                    while (remaining > 0)
                    {
                        int read = fs.Read(buffer, 0, (int)Math.Min(buffer.Length, remaining));
                        if (read <= 0) break;
                        output.Write(buffer, 0, read);
                        remaining -= read;
                    }
                }

                FileStream archiveStream = new FileStream(temp, FileMode.Open, FileAccess.Read, FileShare.Read);
                ZipArchive zip = new ZipArchive(archiveStream, ZipArchiveMode.Read);
                return new PayloadArchive(archiveStream, zip, temp);
            }
        }

        private static void ReadExactly(Stream stream, byte[] buffer)
        {
            int offset = 0;
            while (offset < buffer.Length)
            {
                int read = stream.Read(buffer, offset, buffer.Length - offset);
                if (read <= 0) throw new EndOfStreamException();
                offset += read;
            }
        }

        public void ExtractTo(string destination, Action<int, int> progress)
        {
            Directory.CreateDirectory(destination);
            string root = Path.GetFullPath(destination);
            if (!root.EndsWith(Path.DirectorySeparatorChar.ToString())) root += Path.DirectorySeparatorChar;

            int total = _zip.Entries.Count;
            int done = 0;
            foreach (ZipArchiveEntry entry in _zip.Entries)
            {
                string name = entry.FullName.Replace('/', Path.DirectorySeparatorChar);
                string full = Path.GetFullPath(Path.Combine(root, name));
                if (!full.StartsWith(root, StringComparison.OrdinalIgnoreCase))
                    throw new InvalidDataException("Недопустимый путь в payload.");

                if (name.EndsWith(Path.DirectorySeparatorChar.ToString()))
                {
                    Directory.CreateDirectory(full);
                }
                else
                {
                    Directory.CreateDirectory(Path.GetDirectoryName(full));
                    using (Stream input = entry.Open())
                    using (FileStream output = new FileStream(full, FileMode.Create, FileAccess.Write, FileShare.None))
                    {
                        input.CopyTo(output);
                    }
                }

                done++;
                if (progress != null) progress(done, total);
            }
        }

        public void Dispose()
        {
            if (_zip != null) { _zip.Dispose(); _zip = null; }
            if (_stream != null) { _stream.Dispose(); _stream = null; }
            try { if (_temp != null && File.Exists(_temp)) File.Delete(_temp); }
            catch { }
        }
    }

    internal static class InstallerEngine
    {
        public static bool IsAdmin()
        {
            try
            {
                System.Security.Principal.WindowsIdentity identity = System.Security.Principal.WindowsIdentity.GetCurrent();
                System.Security.Principal.WindowsPrincipal principal = new System.Security.Principal.WindowsPrincipal(identity);
                return principal.IsInRole(System.Security.Principal.WindowsBuiltInRole.Administrator);
            }
            catch { return false; }
        }

        public static bool NeedsElevation(string path)
        {
            string lower = path.ToLowerInvariant();
            if (lower.StartsWith("c:\\program files") || lower.StartsWith("c:\\windows")) return true;
            if (System.Text.RegularExpressions.Regex.IsMatch(lower, "^[a-z]:\\\\[^\\\\]+$")) return true;
            return false;
        }

        public static string DefaultInstallDir()
        {
            string local = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            if (string.IsNullOrEmpty(local))
                local = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "AppData", "Local");
            return Path.Combine(local, "Programs", "LostFilmDesktop");
        }

        public static string DefaultPortableDir()
        {
            return Path.Combine(Path.GetDirectoryName(Application.ExecutablePath), "LostFilmDesktop-portable");
        }

        public static void Install(string target, bool portable, bool desktop, bool autorun, Action<int, int> progress)
        {
            using (PayloadArchive payload = PayloadArchive.Open())
            {
                payload.ExtractTo(target, progress);
            }

            string exe = Path.Combine(target, "LostFilmDesktop.exe");
            if (portable)
            {
                File.WriteAllText(Path.Combine(target, "portable.marker"), "portable");
                string uninstall = Path.Combine(target, "Uninstall.exe");
                if (File.Exists(uninstall)) File.Delete(uninstall);
            }
            else
            {
                CreateShortcuts(target, exe, desktop);
                RegisterUninstall(target, exe);
                if (NeedsElevation(target)) ApplyAcl(target);
                if (autorun) SetAutorun(exe);
            }


        }

        public static void Launch(string exe)
        {
            try { Process.Start("explorer.exe", "\"" + exe + "\""); }
            catch { }
        }

        public static string DesktopPath()
        {
            return Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory);
        }

        public static string ProgramsPath()
        {
            return Environment.GetFolderPath(Environment.SpecialFolder.Programs);
        }

        public static void CreateShortcut(string linkPath, string target, string workDir)
        {
            Type shellType = Type.GetTypeFromProgID("WScript.Shell");
            if (shellType == null) return;
            object shell = Activator.CreateInstance(shellType);
            object shortcut = shellType.InvokeMember("CreateShortcut", BindingFlags.InvokeMethod, null, shell, new object[] { linkPath });
            Type shortcutType = shortcut.GetType();
            shortcutType.InvokeMember("TargetPath", BindingFlags.SetProperty, null, shortcut, new object[] { target });
            shortcutType.InvokeMember("WorkingDirectory", BindingFlags.SetProperty, null, shortcut, new object[] { workDir });
            shortcutType.InvokeMember("IconLocation", BindingFlags.SetProperty, null, shortcut, new object[] { target });
            shortcutType.InvokeMember("Description", BindingFlags.SetProperty, null, shortcut, new object[] { "LostFilm Desktop" });
            shortcutType.InvokeMember("Save", BindingFlags.InvokeMethod, null, shortcut, null);
        }

        private static void CreateShortcuts(string target, string exe, bool desktop)
        {
            try
            {
                if (desktop) CreateShortcut(Path.Combine(DesktopPath(), "LostFilm Desktop.lnk"), exe, target);
                string group = Path.Combine(ProgramsPath(), "LostFilm Desktop");
                Directory.CreateDirectory(group);
                CreateShortcut(Path.Combine(group, "LostFilm Desktop.lnk"), exe, target);
                CreateShortcut(Path.Combine(group, "Удалить LostFilm Desktop.lnk"), Path.Combine(target, "Uninstall.exe"), target);
            }
            catch { }
        }

        private static void RegisterUninstall(string target, string exe)
        {
            try
            {
                using (RegistryKey key = Registry.CurrentUser.CreateSubKey(@"Software\Microsoft\Windows\CurrentVersion\Uninstall\LostFilmDesktop"))
                {
                    if (key == null) return;
                    key.SetValue("DisplayName", "LostFilm Desktop");
                    key.SetValue("DisplayVersion", "1.0.0");
                    key.SetValue("Publisher", "LostFilm Desktop");
                    key.SetValue("InstallLocation", target);
                    key.SetValue("DisplayIcon", exe);
                    key.SetValue("UninstallString", "\"" + Path.Combine(target, "Uninstall.exe") + "\"");
                    key.SetValue("NoModify", 1, RegistryValueKind.DWord);
                    key.SetValue("NoRepair", 1, RegistryValueKind.DWord);
                }
            }
            catch { }
        }

        private static void SetAutorun(string exe)
        {
            try
            {
                using (RegistryKey key = Registry.CurrentUser.CreateSubKey(@"Software\Microsoft\Windows\CurrentVersion\Run"))
                {
                    if (key != null) key.SetValue("LostFilmDesktop", "\"" + exe + "\"");
                }
            }
            catch { }
        }

        private static void ApplyAcl(string target)
        {
            try
            {
                ProcessStartInfo info = new ProcessStartInfo("icacls.exe", "\"" + target + "\" /grant *S-1-5-32-545:(OI)(CI)RX /T /C /Q");
                info.CreateNoWindow = true;
                info.UseShellExecute = false;
                info.WindowStyle = ProcessWindowStyle.Hidden;
                Process process = Process.Start(info);
                if (process != null) process.WaitForExit(30000);
            }
            catch { }
        }
    }

    internal static class Uninstaller
    {
        public static int Run()
        {
            string dir = Path.GetDirectoryName(Application.ExecutablePath);
            try
            {
                string desktop = Path.Combine(InstallerEngine.DesktopPath(), "LostFilm Desktop.lnk");
                if (File.Exists(desktop)) File.Delete(desktop);
            }
            catch { }

            try
            {
                string group = Path.Combine(InstallerEngine.ProgramsPath(), "LostFilm Desktop");
                if (Directory.Exists(group)) Directory.Delete(group, true);
            }
            catch { }

            try
            {
                using (RegistryKey key = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Run", true))
                {
                    if (key != null) key.DeleteValue("LostFilmDesktop", false);
                }
            }
            catch { }

            try { Registry.CurrentUser.DeleteSubKeyTree(@"Software\Microsoft\Windows\CurrentVersion\Uninstall\LostFilmDesktop", false); }
            catch { }

            try
            {
                string args = "/c ping 127.0.0.1 -n 3 > nul & rmdir /s /q \"" + dir + "\"";
                ProcessStartInfo info = new ProcessStartInfo("cmd.exe", args);
                info.CreateNoWindow = true;
                info.UseShellExecute = false;
                info.WindowStyle = ProcessWindowStyle.Hidden;
                Process.Start(info);
            }
            catch { }

            return 0;
        }
    }

    internal enum SetupScreen
    {
        Mode,
        Options,
        Progress,
        Done
    }

    internal sealed class SetupForm : Form
    {
        private const int W = 1000;
        private const int H = 680;

        private static readonly Rectangle RCardInstall = new Rectangle(36, 238, 444, 190);
        private static readonly Rectangle RCardPortable = new Rectangle(496, 238, 444, 190);
        private static readonly Rectangle RNext = new Rectangle(800, 596, 140, 46);
        private static readonly Rectangle RCancel = new Rectangle(694, 596, 96, 46);
        private static readonly Rectangle RClose = new Rectangle(950, 22, 32, 32);
        private static readonly Rectangle RMin = new Rectangle(908, 22, 32, 32);
        private static readonly Rectangle RField = new Rectangle(36, 260, 700, 50);
        private static readonly Rectangle RBrowse = new Rectangle(744, 264, 120, 42);
        private static readonly Rectangle RReset = new Rectangle(36, 316, 180, 20);
        private static readonly Rectangle RChkDesktop = new Rectangle(36, 348, 420, 22);
        private static readonly Rectangle RChkAutorun = new Rectangle(36, 380, 420, 22);
        private static readonly Rectangle RChkRun = new Rectangle(36, 412, 420, 22);
        private static readonly Rectangle RBack = new Rectangle(608, 596, 110, 46);
        private static readonly Rectangle RInstall = new Rectangle(730, 596, 210, 46);
        private static readonly Rectangle ROpen = new Rectangle(360, 388, 150, 44);
        private static readonly Rectangle RRun = new Rectangle(520, 388, 120, 44);

        private readonly List<Hit> _hits = new List<Hit>();
        private readonly Dictionary<string, Font> _fonts = new Dictionary<string, Font>();
        private Bitmap _background;

        private SetupScreen _screen = SetupScreen.Mode;
        private bool _portable;
        private bool _focusPath;
        private string _path = string.Empty;
        private string _hover = string.Empty;
        private bool _dragging;
        private Point _dragStart;
        private bool _chkDesktop = true;
        private bool _chkAutorun;
        private bool _chkRun = true;
        private double _progress;
        private string _status = string.Empty;
        private string _target = string.Empty;
        private string _donePath = string.Empty;
        private bool _caretOn = true;
        private System.Windows.Forms.Timer _caretTimer;

        public SetupForm()
        {
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true);
            DoubleBuffered = true;
            FormBorderStyle = FormBorderStyle.None;
            StartPosition = FormStartPosition.CenterScreen;
            ClientSize = new Size(W, H);
            Text = "LostFilm Desktop — Установка";
            KeyPreview = true;
            BackColor = Theme.Dark;
            try
            {
                if (AppArt.Icon != null) Icon = AppArt.Icon;
            }
            catch
            {
            }

            _path = InstallerEngine.DefaultInstallDir();
            _caretTimer = new System.Windows.Forms.Timer();
            _caretTimer.Interval = 520;
            _caretTimer.Tick += delegate
            {
                _caretOn = !_caretOn;
                if (_focusPath) Invalidate(new Rectangle(RField.X - 2, RField.Y - 2, RField.Width + 4, RField.Height + 4));
            };
            _caretTimer.Start();
            BuildHits();
        }

        public void PreparePreview(string path, SetupScreen screen, bool portable, string targetPath, double progress)
        {
            _screen = screen;
            _portable = portable;
            _path = targetPath;
            _progress = progress;
            _donePath = targetPath;
            _status = "Файлы: 122 из 197";
            BuildHits();
        }

        private Font F(float size, FontStyle style)
        {
            string key = size.ToString("0.0", System.Globalization.CultureInfo.InvariantCulture) + "|" + (int)style;
            Font font;
            if (!_fonts.TryGetValue(key, out font))
            {
                font = new Font("Segoe UI", size, style, GraphicsUnit.Point);
                _fonts[key] = font;
            }
            return font;
        }

        protected override void OnPaintBackground(PaintEventArgs e)
        {
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TextRenderingHint = TextRenderingHint.AntiAliasGridFit;
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;

            g.DrawImageUnscaled(GetBackground(), 0, 0);
            BuildHits();
            DrawHeader(g);
            DrawStepper(g);

            if (_screen == SetupScreen.Mode) DrawMode(g);
            else if (_screen == SetupScreen.Options) DrawOptions(g);
            else if (_screen == SetupScreen.Progress) DrawProgress(g);
            else DrawDone(g);
        }

        private Bitmap GetBackground()
        {
            if (_background != null) return _background;
            Bitmap bmp = new Bitmap(W, H);
            using (Graphics g = Graphics.FromImage(bmp))
            {
                g.SmoothingMode = SmoothingMode.AntiAlias;
                using (LinearGradientBrush baseBrush = new LinearGradientBrush(new Rectangle(0, 0, W, H), Color.FromArgb(10, 12, 18), Color.FromArgb(9, 13, 26), 90f))
                {
                    g.FillRectangle(baseBrush, 0, 0, W, H);
                }

                Draw.Glow(g, new RectangleF(-360, -300, 1050, 760), Theme.AccentA, 120);
                Draw.Glow(g, new RectangleF(560, 300, 980, 820), Theme.AccentB, 110);
                Draw.Glow(g, new RectangleF(280, 260, 520, 420), Color.FromArgb(43, 217, 200), 55);

                DrawCinemaArt(g);

                using (LinearGradientBrush top = new LinearGradientBrush(new Rectangle(0, 0, W, 140), Color.FromArgb(150, 0, 0, 0), Color.FromArgb(0, 0, 0, 0), 90f))
                    g.FillRectangle(top, 0, 0, W, 140);
                using (LinearGradientBrush bottom = new LinearGradientBrush(new Rectangle(0, H - 160, W, 160), Color.FromArgb(0, 0, 0, 0), Color.FromArgb(170, 0, 0, 0), 90f))
                    g.FillRectangle(bottom, 0, H - 160, W, 160);
                using (LinearGradientBrush left = new LinearGradientBrush(new Rectangle(0, 0, 200, H), Color.FromArgb(120, 0, 0, 0), Color.FromArgb(0, 0, 0, 0), 0f))
                    g.FillRectangle(left, 0, 0, 200, H);
                using (LinearGradientBrush right = new LinearGradientBrush(new Rectangle(W - 200, 0, 200, H), Color.FromArgb(0, 0, 0, 0), Color.FromArgb(120, 0, 0, 0), 0f))
                    g.FillRectangle(right, W - 200, 0, 200, H);

                using (Pen border = new Pen(Color.FromArgb(40, 255, 255, 255), 1f))
                {
                    g.DrawRectangle(border, 0, 0, W - 1, H - 1);
                }
            }
            _background = bmp;
            return bmp;
        }

        private static PointF Polar(PointF origin, float degrees, float length)
        {
            double rad = degrees * Math.PI / 180.0;
            return new PointF(origin.X + (float)(Math.Cos(rad) * length), origin.Y + (float)(Math.Sin(rad) * length));
        }

        private static void DrawCinemaArt(Graphics g)
        {
            PointF origin = new PointF(930f, -60f);
            float[] angles = new float[] { 116f, 130f, 144f, 158f };
            for (int i = 0; i < angles.Length; i++)
            {
                PointF p1 = Polar(origin, angles[i] - 4f, 980f);
                PointF p2 = Polar(origin, angles[i] + 4f, 980f);
                using (GraphicsPath path = new GraphicsPath())
                {
                    path.AddPolygon(new PointF[] { origin, p1, p2 });
                    using (PathGradientBrush brush = new PathGradientBrush(path))
                    {
                        brush.CenterPoint = origin;
                        brush.CenterColor = Color.FromArgb(34, 170, 225, 255);
                        Color[] surround = new Color[path.PointCount];
                        for (int k = 0; k < surround.Length; k++) surround[k] = Color.FromArgb(0, 170, 225, 255);
                        brush.SurroundColors = surround;
                        g.FillPath(brush, path);
                    }
                }
            }

            float cx = 792f;
            float cy = 30f;
            float radius = 110f;
            RectangleF reel = new RectangleF(cx - radius, cy - radius, radius * 2f, radius * 2f);
            Draw.Glow(g, RectangleF.Inflate(reel, 90f, 90f), Theme.AccentB, 85);

            using (Pen halo = new Pen(Color.FromArgb(34, 79, 195, 247), 26f))
                g.DrawEllipse(halo, reel);

            using (SolidBrush dark = new SolidBrush(Color.FromArgb(140, 8, 10, 16)))
                g.FillEllipse(dark, RectangleF.Inflate(reel, -6f, -6f));

            using (LinearGradientBrush gradient = new LinearGradientBrush(reel, Theme.AccentA, Theme.AccentB, 45f))
            using (Pen ring = new Pen(gradient, 7f))
                g.DrawEllipse(ring, reel);

            for (int i = 0; i < 6; i++)
            {
                double rad = (i * 60.0) * Math.PI / 180.0;
                float hx = cx + (float)(Math.Cos(rad) * 64.0);
                float hy = cy + (float)(Math.Sin(rad) * 64.0);
                RectangleF hole = new RectangleF(hx - 15f, hy - 15f, 30f, 30f);
                using (SolidBrush fill = new SolidBrush(Color.FromArgb(235, 6, 8, 12)))
                    g.FillEllipse(fill, hole);
                using (Pen rim = new Pen(Color.FromArgb(120, 79, 195, 247), 2f))
                    g.DrawEllipse(rim, hole);
            }

            RectangleF hub = new RectangleF(cx - 22f, cy - 22f, 44f, 44f);
            using (SolidBrush fill = new SolidBrush(Color.FromArgb(235, 6, 8, 12)))
                g.FillEllipse(fill, hub);
            using (LinearGradientBrush gradient = new LinearGradientBrush(hub, Theme.AccentA, Theme.AccentB, 45f))
            using (Pen ring = new Pen(gradient, 5f))
                g.DrawEllipse(ring, hub);

            float[,] sparks = new float[,] {
                { 676f, 92f }, { 706f, 148f }, { 738f, 58f }, { 862f, 128f },
                { 896f, 76f }, { 686f, 34f }, { 812f, 164f }, { 840f, 8f }
            };
            for (int i = 0; i < sparks.GetLength(0); i++)
            {
                float x = sparks[i, 0];
                float y = sparks[i, 1];
                Draw.Glow(g, new RectangleF(x - 14f, y - 14f, 28f, 28f), Theme.AccentA, 120);
                using (SolidBrush dot = new SolidBrush(Color.FromArgb(210, 220, 245, 255)))
                    g.FillEllipse(dot, x - 1.8f, y - 1.8f, 3.6f, 3.6f);
            }
        }

        private void DrawHeader(Graphics g)
        {
            RectangleF logo = new RectangleF(36, 20, 44, 44);
            Bitmap appIcon = AppArt.Bitmap;
            if (appIcon != null)
            {
                using (GraphicsPath clip = Draw.Round(logo, 13))
                {
                    GraphicsState state = g.Save();
                    g.SetClip(clip);
                    g.DrawImage(appIcon, logo);
                    g.Restore(state);
                }
                using (Pen border = new Pen(Color.FromArgb(70, 255, 255, 255), 1f))
                using (GraphicsPath outline = Draw.Round(logo, 13))
                {
                    g.DrawPath(border, outline);
                }
            }
            else
            {
                Draw.GradientRound(g, logo, 14);
                Draw.PlayTriangle(g, logo, Color.FromArgb(11, 13, 19));
            }

            Draw.Text(g, "LostFilm Desktop", F(16.5f, FontStyle.Bold), Theme.Text, 94, 23);
            Draw.Text(g, "Установка приложения", F(11f, FontStyle.Regular), Theme.Muted, 94, 47);

            float chipX = 94 + Draw.Measure(g, "LostFilm Desktop", F(16.5f, FontStyle.Bold)) + 14;
            RectangleF chip = new RectangleF(chipX, 30, 62, 22);
            Draw.FillRound(g, Color.FromArgb(26, 255, 255, 255), chip, 8);
            Draw.TextCenter(g, "v1.0.0", F(10f, FontStyle.Regular), Color.FromArgb(195, 205, 221), chip);

            DrawWindowButton(g, RMin, "min", false);
            DrawWindowButton(g, RClose, "close", true);
        }

        private void DrawWindowButton(Graphics g, Rectangle r, string id, bool close)
        {
            bool hover = _hover == id;
            if (hover) Draw.FillRound(g, close ? Color.FromArgb(190, 220, 70, 80) : Color.FromArgb(30, 255, 255, 255), r, 9);
            Color color = hover ? Color.White : Color.FromArgb(185, 195, 212);
            using (Pen pen = new Pen(color, 1.6f))
            {
                if (id == "min")
                {
                    g.DrawLine(pen, r.X + 11, r.Y + 17, r.X + 21, r.Y + 17);
                }
                else
                {
                    g.DrawLine(pen, r.X + 11, r.Y + 11, r.X + 21, r.Y + 21);
                    g.DrawLine(pen, r.X + 21, r.Y + 11, r.X + 11, r.Y + 21);
                }
            }
        }

        private void DrawStepper(Graphics g)
        {
            int active = _screen == SetupScreen.Mode ? 0 : (_screen == SetupScreen.Done ? 2 : 1);
            string[] labels = { "Режим", "Параметры", "Готово" };
            float x = 36;
            for (int i = 0; i < 3; i++)
            {
                RectangleF dot = new RectangleF(x, 96, 26, 26);
                bool doneOrActive = i <= active;
                if (doneOrActive) Draw.GradientRound(g, dot, 13);
                else Draw.FillRound(g, Color.FromArgb(20, 255, 255, 255), dot, 13);

                Draw.TextCenter(g, (i + 1).ToString(), F(11.5f, FontStyle.Bold),
                    doneOrActive ? Color.FromArgb(11, 13, 19) : Theme.Muted, dot);

                Draw.Text(g, labels[i], F(12.5f, i == active ? FontStyle.Bold : FontStyle.Regular),
                    i == active ? Theme.Text : Theme.Muted, x + 36, 101);

                float width = Draw.Measure(g, labels[i], F(12.5f, FontStyle.Regular));
                x += 36 + width + 18;
                if (i < 2)
                {
                    using (Pen pen = new Pen(Color.FromArgb(30, 255, 255, 255), 1f))
                    {
                        g.DrawLine(pen, x, 109, x + 34, 109);
                    }
                    x += 50;
                }
            }
        }

        private void DrawEyebrow(Graphics g, string text, float y)
        {
            Draw.Text(g, text, F(10f, FontStyle.Bold), Color.FromArgb(127, 140, 163), 36, y);
        }

        private void DrawMode(Graphics g)
        {
            DrawEyebrow(g, "УСТАНОВКА", 150);
            Draw.Text(g, "Как установить?", F(27f, FontStyle.Bold), Theme.Text, 36, 168);
            Draw.Text(g, "Выберите режим — установка в систему или портативная папка.", F(12f, FontStyle.Regular), Theme.Muted, 36, 208);

            DrawCard(g, RCardInstall, !_portable, "Установка", "Ярлыки и удаление в системе",
                "Файлы разместятся в выбранной папке, появятся ярлык в меню «Пуск» и запись в «Приложениях».",
                "≈ 180 МБ", true, 0);
            DrawCard(g, RCardPortable, _portable, "Портативная версия", "Без записи в систему",
                "Просто распакуется в выбранную папку. Можно носить на флешке, ярлыки и реестр не создаются.",
                "≈ 185 МБ", false, 1);

            Draw.Text(g, GetStorageText(), F(11f, FontStyle.Regular), Theme.Muted, 36, 614);
            DrawButton(g, RCancel, "Отмена", false, _hover == "cancel", true);
            DrawButton(g, RNext, "Далее  ›", true, _hover == "next", true);
        }

        private void DrawOptions(Graphics g)
        {
            DrawEyebrow(g, _portable ? "ПОРТАТИВНАЯ ВЕРСИЯ" : "УСТАНОВКА", 150);
            Draw.Text(g, _portable ? "Куда распаковать?" : "Куда установить?", F(27f, FontStyle.Bold), Theme.Text, 36, 168);
            Draw.Text(g, _portable ? "Файлы будут распакованы в указанную папку." : "Приложение будет установлено в указанную папку.",
                F(12f, FontStyle.Regular), Theme.Muted, 36, 208);

            Draw.Text(g, _portable ? "Папка портативной версии" : "Папка установки", F(11f, FontStyle.Bold), Theme.Muted, 36, 238);
            DrawPathField(g);
            DrawButton(g, RBrowse, "Обзор…", false, _hover == "browse", true);

            Draw.Text(g, "По умолчанию", F(11f, FontStyle.Regular), Color.FromArgb(124, 203, 255), 36, 318);
            using (Pen pen = new Pen(Color.FromArgb(90, 124, 203, 255), 1f))
            {
                float w = Draw.Measure(g, "По умолчанию", F(11f, FontStyle.Regular));
                g.DrawLine(pen, 36, 333, 36 + w, 333);
            }

            DrawCheck(g, RChkDesktop, _chkDesktop, "Создать ярлык на рабочем столе", !_portable);
            if (!_portable) DrawCheck(g, RChkAutorun, _chkAutorun, "Запускать при входе в Windows", true);
            DrawCheck(g, RChkRun, _chkRun, "Запустить после установки", true);

            Draw.Text(g, GetStorageText(), F(11f, FontStyle.Regular), Theme.Muted, 36, 614);
            DrawButton(g, RBack, "Назад", false, _hover == "back", true);
            DrawButton(g, RInstall, _portable ? "Распаковать  ›" : "Установить  ›", true, _hover == "install", true);
        }

        private void DrawPathField(Graphics g)
        {
            RectangleF field = RField;
            Color border = _focusPath ? Theme.AccentA : Color.FromArgb(38, 255, 255, 255);
            using (GraphicsPath path = Draw.Round(field, 14))
            {
                using (SolidBrush fill = new SolidBrush(Color.FromArgb(18, 255, 255, 255))) g.FillPath(fill, path);
                using (Pen pen = new Pen(border, _focusPath ? 1.6f : 1f)) g.DrawPath(pen, path);
            }

            using (Pen pen = new Pen(Color.FromArgb(143, 160, 184), 1.7f))
            {
                float fx = field.X + 16;
                float fy = field.Y + 18;
                g.DrawRectangle(pen, fx, fy + 4, 18, 12);
                g.DrawLine(pen, fx, fy + 4, fx + 5, fy);
                g.DrawLine(pen, fx + 5, fy, fx + 9, fy + 4);
            }

            RectangleF textRect = new RectangleF(field.X + 48, field.Y + 14, field.Width - 160, field.Height - 24);
            using (StringFormat format = new StringFormat())
            {
                format.Trimming = StringTrimming.EllipsisPath;
                format.FormatFlags = StringFormatFlags.NoWrap;
                using (SolidBrush brush = new SolidBrush(Theme.Text))
                {
                    g.DrawString(_path, F(12.5f, FontStyle.Regular), brush, textRect, format);
                }
            }

            if (_focusPath && _caretOn)
            {
                float width = Draw.Measure(g, _path, F(12.5f, FontStyle.Regular));
                float caretX = Math.Min(textRect.Right - 2, textRect.X + width + 2);
                using (Pen pen = new Pen(Theme.AccentA, 1.4f))
                {
                    g.DrawLine(pen, caretX, textRect.Y + 1, caretX, textRect.Bottom - 1);
                }
            }
        }

        private void DrawCheck(Graphics g, Rectangle r, bool value, string label, bool enabled)
        {
            RectangleF box = new RectangleF(r.X, r.Y, 19, 19);
            Color textColor = enabled ? Theme.Text : Color.FromArgb(90, 147, 160, 180);
            if (value && enabled)
            {
                using (GraphicsPath path = Draw.Round(box, 6))
                using (LinearGradientBrush brush = new LinearGradientBrush(box, Theme.AccentA, Theme.AccentB, 45f))
                {
                    g.FillPath(brush, path);
                }
                Draw.CheckMark(g, box, Color.FromArgb(11, 13, 19), 2.1f);
            }
            else
            {
                using (GraphicsPath path = Draw.Round(box, 6))
                {
                    using (SolidBrush fill = new SolidBrush(Color.FromArgb(16, 255, 255, 255))) g.FillPath(fill, path);
                    using (Pen pen = new Pen(Color.FromArgb(48, 255, 255, 255), 1f)) g.DrawPath(pen, path);
                }
            }

            Draw.Text(g, label, F(12f, FontStyle.Regular), textColor, r.X + 28, r.Y + 1);
        }

        private void DrawCard(Graphics g, Rectangle r, bool selected, string title, string subtitle, string desc, string sizeText, bool badge, int icon)
        {
            RectangleF rect = r;
            using (GraphicsPath path = Draw.Round(rect, 18))
            {
                using (SolidBrush fill = new SolidBrush(Color.FromArgb(selected ? 28 : 14, 255, 255, 255)))
                    g.FillPath(fill, path);

                if (selected)
                {
                    Draw.GlowRing(g, rect, 18, Theme.AccentA, 90, 1.6f);
                    using (LinearGradientBrush brush = new LinearGradientBrush(rect, Theme.AccentA, Theme.AccentB, 45f))
                    using (Pen pen = new Pen(brush, 2f))
                    {
                        g.DrawPath(pen, path);
                    }
                }
                else
                {
                    using (Pen pen = new Pen(Color.FromArgb(36, 255, 255, 255), 1.1f))
                    {
                        g.DrawPath(pen, path);
                    }
                }
            }

            RectangleF iconRect = new RectangleF(r.X + 18, r.Y + 18, 46, 46);
            Draw.FillRound(g, Color.FromArgb(selected ? 60 : 36, 79, 195, 247), iconRect, 14);
            DrawCardIcon(g, iconRect, icon);

            Draw.Text(g, title, F(15.5f, FontStyle.Bold), Theme.Text, r.X + 76, r.Y + 20);
            Draw.Text(g, subtitle, F(10.5f, FontStyle.Regular), Theme.Muted, r.X + 76, r.Y + 44);

            if (badge)
            {
                Font bf = F(8.5f, FontStyle.Bold);
                float bw = Draw.Measure(g, "РЕКОМЕНДУЕТСЯ", bf) + 16;
                RectangleF br = new RectangleF(r.Right - bw - 16, r.Y + 16, bw, 20);
                Draw.FillRound(g, Color.FromArgb(46, 79, 195, 247), br, 9);
                Draw.TextCenter(g, "РЕКОМЕНДУЕТСЯ", bf, Color.FromArgb(191, 230, 255), br);
            }

            Draw.TextWrapped(g, desc, F(11f, FontStyle.Regular), Theme.Muted,
                new RectangleF(r.X + 18, r.Y + 82, r.Width - 36, 74));

            Draw.Text(g, sizeText, F(11f, FontStyle.Regular), Color.FromArgb(174, 185, 203), r.X + 18, r.Bottom - 34);

            RectangleF radio = new RectangleF(r.Right - 36, r.Bottom - 33, 18, 18);
            using (Pen pen = new Pen(selected ? Theme.AccentA : Color.FromArgb(58, 67, 84), 2f))
            {
                g.DrawEllipse(pen, radio);
            }
            if (selected)
            {
                using (SolidBrush brush = new SolidBrush(Theme.AccentA))
                {
                    g.FillEllipse(brush, radio.X + 5, radio.Y + 5, 8, 8);
                }
            }
        }

        private void DrawCardIcon(Graphics g, RectangleF box, int icon)
        {
            using (Pen pen = new Pen(Color.FromArgb(207, 233, 255), 1.9f))
            {
                if (icon == 0)
                {
                    g.DrawRectangle(pen, box.X + 14, box.Y + 14, 18, 13);
                    g.DrawLine(pen, box.X + 19, box.Y + 32, box.X + 27, box.Y + 32);
                    g.DrawLine(pen, box.X + 23, box.Y + 27, box.X + 23, box.Y + 32);
                }
                else
                {
                    g.DrawRectangle(pen, box.X + 13, box.Y + 19, 20, 12);
                    g.DrawLine(pen, box.X + 13, box.Y + 19, box.X + 17, box.Y + 13);
                    g.DrawLine(pen, box.X + 17, box.Y + 13, box.X + 29, box.Y + 13);
                    g.DrawLine(pen, box.X + 29, box.Y + 13, box.X + 33, box.Y + 19);
                    g.DrawLine(pen, box.X + 20, box.Y + 25, box.X + 26, box.Y + 25);
                }
            }
        }

        private void DrawButton(Graphics g, Rectangle r, string text, bool primary, bool hover, bool enabled)
        {
            RectangleF rect = r;
            float radius = 13f;
            if (primary)
            {
                using (GraphicsPath path = Draw.Round(rect, radius))
                using (LinearGradientBrush brush = new LinearGradientBrush(rect, Theme.AccentA, Theme.AccentB, 45f))
                {
                    g.FillPath(brush, path);
                    if (hover)
                    {
                        using (SolidBrush overlay = new SolidBrush(Color.FromArgb(26, 255, 255, 255))) g.FillPath(overlay, path);
                    }
                }
                Draw.TextCenter(g, text, F(13f, FontStyle.Bold), Color.FromArgb(11, 13, 19), rect);
            }
            else
            {
                using (GraphicsPath path = Draw.Round(rect, radius))
                using (SolidBrush fill = new SolidBrush(hover ? Color.FromArgb(24, 255, 255, 255) : Color.FromArgb(10, 255, 255, 255)))
                {
                    g.FillPath(fill, path);
                }
                Draw.TextCenter(g, text, F(12.5f, FontStyle.Regular), Theme.Muted, rect);
            }
        }

        private void DrawProgress(Graphics g)
        {
            Draw.TextCenter(g, string.Format("{0:0}%", _progress), F(46f, FontStyle.Bold), Theme.Text,
                new RectangleF(0, 236, W, 70));
            Draw.TextCenter(g, _portable ? "Распаковываем…" : "Устанавливаем…", F(13.5f, FontStyle.Regular), Theme.Text,
                new RectangleF(0, 304, W, 24));
            Draw.TextCenter(g, _status, F(11.5f, FontStyle.Regular), Theme.Muted, new RectangleF(0, 326, W, 22));

            RectangleF track = new RectangleF(250, 360, 500, 10);
            Draw.FillRound(g, Color.FromArgb(26, 255, 255, 255), track, 5);
            float width = track.Width * (float)(_progress / 100.0);
            if (width > 4)
            {
                RectangleF fill = new RectangleF(track.X, track.Y, width, track.Height);
                using (GraphicsPath path = Draw.Round(fill, 5))
                using (LinearGradientBrush brush = new LinearGradientBrush(fill, Theme.AccentA, Theme.AccentB, 0f))
                {
                    g.FillPath(brush, path);
                }
            }
        }

        private void DrawDone(Graphics g)
        {
            RectangleF circle = new RectangleF(W / 2f - 33, 200, 66, 66);
            Draw.GradientRound(g, circle, 33);
            Draw.CheckMark(g, circle, Color.FromArgb(11, 13, 19), 4.2f);

            Draw.TextCenter(g, "Готово", F(24f, FontStyle.Bold), Theme.Text, new RectangleF(0, 290, W, 40));
            Draw.TextCenter(g, _donePath, F(11.5f, FontStyle.Regular), Theme.Muted, new RectangleF(120, 334, W - 240, 24));

            DrawButton(g, ROpen, "Открыть папку", false, _hover == "open", true);
            DrawButton(g, RRun, "Закрыть", true, _hover == "run", true);
        }

        private string GetStorageText()
        {
            try
            {
                string root = Path.GetPathRoot(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData));
                DriveInfo info = new DriveInfo(root);
                double freeGb = Math.Round(info.AvailableFreeSpace / 1024.0 / 1024.0 / 1024.0, 1);
                return string.Format("Свободно {0:0.0} ГБ · требуется ≈ 180 МБ", freeGb);
            }
            catch
            {
                return "Требуется ≈ 180 МБ";
            }
        }

        private void BuildHits()
        {
            _hits.Clear();
            _hits.Add(new Hit(RClose, "close"));
            _hits.Add(new Hit(RMin, "min"));
            if (_screen == SetupScreen.Mode)
            {
                _hits.Add(new Hit(RCardInstall, "card:install"));
                _hits.Add(new Hit(RCardPortable, "card:portable"));
                _hits.Add(new Hit(RNext, "next"));
                _hits.Add(new Hit(RCancel, "cancel"));
            }
            else if (_screen == SetupScreen.Options)
            {
                _hits.Add(new Hit(RField, "field"));
                _hits.Add(new Hit(RBrowse, "browse"));
                _hits.Add(new Hit(RReset, "reset"));
                _hits.Add(new Hit(RChkDesktop, "chk:desktop"));
                if (!_portable) _hits.Add(new Hit(RChkAutorun, "chk:autorun"));
                _hits.Add(new Hit(RChkRun, "chk:run"));
                _hits.Add(new Hit(RBack, "back"));
                _hits.Add(new Hit(RInstall, "install"));
            }
            else if (_screen == SetupScreen.Done)
            {
                _hits.Add(new Hit(ROpen, "open"));
                _hits.Add(new Hit(RRun, "run"));
            }
        }

        private Hit HitAt(Point point)
        {
            for (int i = _hits.Count - 1; i >= 0; i--)
            {
                if (_hits[i].Bounds.Contains(point)) return _hits[i];
            }
            return null;
        }

        protected override void OnMouseMove(MouseEventArgs e)
        {
            base.OnMouseMove(e);
            if (_dragging)
            {
                Point delta = Control.MousePosition;
                Location = new Point(delta.X - _dragStart.X, delta.Y - _dragStart.Y);
                return;
            }

            string hover = string.Empty;
            Hit hit = HitAt(e.Location);
            if (hit != null) hover = hit.Id;
            if (hover != _hover)
            {
                _hover = hover;
                Cursor = hit != null ? Cursors.Hand : Cursors.Default;
                Invalidate();
            }
        }

        protected override void OnMouseLeave(EventArgs e)
        {
            base.OnMouseLeave(e);
            if (_hover.Length > 0)
            {
                _hover = string.Empty;
                Invalidate();
            }
        }

        protected override void OnMouseDown(MouseEventArgs e)
        {
            base.OnMouseDown(e);
            if (e.Button != MouseButtons.Left) return;
            Hit hit = HitAt(e.Location);
            if (hit == null && e.Y < 78)
            {
                _dragging = true;
                _dragStart = new Point(e.X, e.Y);
            }
        }

        protected override void OnMouseUp(MouseEventArgs e)
        {
            base.OnMouseUp(e);
            _dragging = false;
            if (e.Button != MouseButtons.Left) return;
            Hit hit = HitAt(e.Location);
            if (hit == null)
            {
                _focusPath = false;
                Invalidate();
                return;
            }
            RunHit(hit.Id);
        }

        private void RunHit(string id)
        {
            if (id == "close" || id == "cancel") { Close(); return; }
            if (id == "min") { WindowState = FormWindowState.Minimized; return; }

            if (id == "card:install") { SetMode(false); return; }
            if (id == "card:portable") { SetMode(true); return; }
            if (id == "next")
            {
                _screen = SetupScreen.Options;
                _focusPath = false;
                Invalidate();
                return;
            }
            if (id == "back")
            {
                _screen = SetupScreen.Mode;
                _focusPath = false;
                Invalidate();
                return;
            }
            if (id == "field") { _focusPath = true; _caretOn = true; Invalidate(); return; }
            if (id == "browse")
            {
                using (FolderBrowserDialog dialog = new FolderBrowserDialog())
                {
                    dialog.Description = "Выберите папку";
                    dialog.SelectedPath = _path;
                    if (dialog.ShowDialog(this) == DialogResult.OK)
                    {
                        _path = dialog.SelectedPath;
                        Invalidate();
                    }
                }
                return;
            }
            if (id == "reset") { _path = _portable ? InstallerEngine.DefaultPortableDir() : InstallerEngine.DefaultInstallDir(); Invalidate(); return; }
            if (id == "chk:desktop") { _chkDesktop = !_chkDesktop; Invalidate(); return; }
            if (id == "chk:autorun") { _chkAutorun = !_chkAutorun; Invalidate(); return; }
            if (id == "chk:run") { _chkRun = !_chkRun; Invalidate(); return; }
            if (id == "install") { StartInstall(); return; }
            if (id == "open") { try { Process.Start("explorer.exe", _donePath); } catch { } return; }
            if (id == "run")
            {
                if (_chkRun)
                {
                    try { InstallerEngine.Launch(Path.Combine(_donePath, "LostFilmDesktop.exe")); } catch { }
                }
                Close();
                return;
            }
        }

        private void SetMode(bool portable)
        {
            _portable = portable;
            _path = portable ? InstallerEngine.DefaultPortableDir() : InstallerEngine.DefaultInstallDir();
            _focusPath = false;
            Invalidate();
        }

        protected override void OnKeyDown(KeyEventArgs e)
        {
            base.OnKeyDown(e);
            if (e.KeyCode == Keys.Escape) { Close(); return; }
            if (!_focusPath) return;
            if (e.KeyCode == Keys.Back && _path.Length > 0)
            {
                _path = _path.Substring(0, _path.Length - 1);
                Invalidate();
                e.Handled = true;
            }
        }

        protected override void OnKeyPress(KeyPressEventArgs e)
        {
            base.OnKeyPress(e);
            if (!_focusPath) return;
            char ch = e.KeyChar;
            if (ch == '\b') { e.Handled = true; return; }
            if (ch < 32) return;
            if (ch == '"' || ch == '|' || ch == '*' || ch == '?' || ch == '<' || ch == '>') { e.Handled = true; return; }
            _path += ch;
            _caretOn = true;
            Invalidate();
            e.Handled = true;
        }

        private void StartInstall()
        {
            string target = _path.Trim().Trim('"');
            if (target.Length == 0)
            {
                MessageBox.Show(this, "Укажите папку.", "LostFilm Desktop", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }
            try { target = Path.GetFullPath(target); }
            catch
            {
                MessageBox.Show(this, "Некорректный путь.", "LostFilm Desktop", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }

            if (!InstallerEngine.IsAdmin() && InstallerEngine.NeedsElevation(target))
            {
                MessageBox.Show(this, "Для записи в Program Files нужны права администратора. По умолчанию установка идёт в профиль пользователя — выберите её или запустите установщик от имени администратора.",
                    "LostFilm Desktop", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }

            _target = target;
            _screen = SetupScreen.Progress;
            _progress = 0;
            _status = "Подготовка";
            _focusPath = false;
            BuildHits();
            Invalidate();

            Thread thread = new Thread(delegate()
            {
                try
                {
                    InstallerEngine.Install(target, _portable, _chkDesktop, _chkAutorun, delegate(int done, int total)
                    {
                        Post(delegate
                        {
                            _progress = total > 0 ? 100.0 * done / total : 0;
                            _status = string.Format("Файлы: {0} из {1}", done, total);
                            Invalidate();
                        });
                    });

                    Post(delegate
                    {
                        _donePath = target;
                        _progress = 100;
                        _screen = SetupScreen.Done;
                        BuildHits();
                        Invalidate();
                    });
                }
                catch (Exception error)
                {
                    Post(delegate
                    {
                        _screen = SetupScreen.Options;
                        BuildHits();
                        Invalidate();
                        MessageBox.Show(this, "Ошибка установки: " + error.Message, "LostFilm Desktop", MessageBoxButtons.OK, MessageBoxIcon.Error);
                    });
                }
            });
            thread.IsBackground = true;
            thread.Start();
        }

        private void Post(Action action)
        {
            if (IsDisposed || !IsHandleCreated) return;
            try { BeginInvoke(action); }
            catch { }
        }

        protected override void Dispose(bool disposing)
        {
            if (disposing)
            {
                foreach (Font font in _fonts.Values) font.Dispose();
                if (_background != null) _background.Dispose();
                if (_caretTimer != null) _caretTimer.Dispose();
            }
            base.Dispose(disposing);
        }
    }

    internal static class Preview
    {
        public static int Render(string directory)
        {
            Directory.CreateDirectory(directory);
            RenderOne(directory, "01-mode.png", SetupScreen.Mode, false, InstallerEngine.DefaultInstallDir(), 0, "");
            RenderOne(directory, "02-mode-portable.png", SetupScreen.Mode, true, InstallerEngine.DefaultPortableDir(), 0, "");
            RenderOne(directory, "03-options.png", SetupScreen.Options, false, InstallerEngine.DefaultInstallDir(), 0, "");
            RenderOne(directory, "04-options-portable.png", SetupScreen.Options, true, InstallerEngine.DefaultPortableDir(), 0, "");
            RenderOne(directory, "05-progress.png", SetupScreen.Progress, false, InstallerEngine.DefaultInstallDir(), 62, "");
            RenderOne(directory, "06-done.png", SetupScreen.Done, false, InstallerEngine.DefaultInstallDir(), 100, "");
            return 0;
        }

        private static void RenderOne(string directory, string name, SetupScreen screen, bool portable, string path, double progress, string status)
        {
            using (SetupForm form = new SetupForm())
            {
                form.PreparePreview(path, screen, portable, path, progress);
                form.ShowInTaskbar = false;
                form.StartPosition = FormStartPosition.Manual;
                form.Location = new Point(-4000, -4000);
                form.Show();
                Application.DoEvents();
                using (Bitmap bmp = new Bitmap(form.ClientSize.Width, form.ClientSize.Height))
                {
                    form.DrawToBitmap(bmp, new Rectangle(0, 0, bmp.Width, bmp.Height));
                    bmp.Save(Path.Combine(directory, name), System.Drawing.Imaging.ImageFormat.Png);
                }
                form.Close();
            }
        }
    }

    internal static class InstallerApp
    {
        public static int Run(string[] args)
        {
            if (args != null && args.Length >= 1)
            {
                string command = args[0].ToLowerInvariant();
                if (command == "/extract" && args.Length >= 2)
                {
                    using (PayloadArchive payload = PayloadArchive.Open())
                    {
                        payload.ExtractTo(args[1], null);
                    }
                    return 0;
                }
                if (command == "/preview" && args.Length >= 2)
                {
                    return Preview.Render(args[1]);
                }
                if (command == "/install" && args.Length >= 2)
                {
                    bool portable = Array.IndexOf(args, "/portable") >= 0;
                    bool noShortcuts = Array.IndexOf(args, "/noshortcuts") >= 0;
                    bool runAfter = Array.IndexOf(args, "/run") >= 0;
                    InstallerEngine.Install(args[1], portable, !noShortcuts, false, null);
                    if (runAfter) InstallerEngine.Launch(Path.Combine(args[1], "LostFilmDesktop.exe"));
                    return 0;
                }
            }

            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            using (SetupForm form = new SetupForm())
            {
                form.ShowDialog();
            }
            return 0;
        }
    }
}