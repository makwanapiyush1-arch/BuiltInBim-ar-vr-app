import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() => runApp(const VillaApp());

/// Bridge to the native viewers: SceneView + ARCore on Android, SceneKit + ARKit on iOS.
const _viewer = MethodChannel('uedc_ss_villa/viewer');

class VillaApp extends StatelessWidget {
  const VillaApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'UEDC_SS Villa',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF1F6FEB), brightness: Brightness.dark),
        useMaterial3: true,
      ),
      home: const HomeScreen(),
    );
  }
}

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  String _model = '';

  @override
  void initState() {
    super.initState();
    _call('currentModel');
  }

  Future<void> _call(String method) async {
    try {
      final result = await _viewer.invokeMethod<String>(method);
      if (result != null && mounted) setState(() => _model = result);
    } on PlatformException catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message ?? e.code)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            const SizedBox(height: 12),
            Text('UEDC_SS Villa', style: theme.textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.bold)),
            const SizedBox(height: 4),
            Text('Model: $_model', style: theme.textTheme.bodyMedium),
            const SizedBox(height: 24),
            _ModeCard(
              icon: Icons.threed_rotation,
              title: '3D View',
              subtitle: 'Drag to orbit, pinch to zoom, two fingers to pan.',
              onTap: () => _call('open3d'),
            ),
            _ModeCard(
              icon: Icons.view_in_ar,
              title: 'AR View',
              subtitle: 'No marker, no floor scan. Tap "Place" and the villa appears in front of the camera '
                  'and stays put while you walk around it.',
              onTap: () => _call('openAr'),
            ),
            _ModeCard(
              icon: Icons.vrpano,
              title: 'VR View',
              subtitle: 'Side-by-side stereo for a Cardboard-style headset. Turn your head to look around.',
              onTap: () => _call('openVr'),
            ),
            const SizedBox(height: 16),
            OutlinedButton.icon(
              icon: const Icon(Icons.folder_open),
              label: const Text('Open model from phone'),
              onPressed: () => _call('pickModel'),
            ),
            TextButton(
              onPressed: () => _call('useBundledModel'),
              child: const Text('Use bundled villa model'),
            ),
          ],
        ),
      ),
    );
  }
}

class _ModeCard extends StatelessWidget {
  const _ModeCard({required this.icon, required this.title, required this.subtitle, required this.onTap});

  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        leading: Icon(icon, size: 40),
        title: Text(title, style: Theme.of(context).textTheme.titleLarge),
        subtitle: Text(subtitle),
        onTap: onTap,
      ),
    );
  }
}
