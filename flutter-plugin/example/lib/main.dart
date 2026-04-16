import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:generic_transfer_encrypt_flutter/flutter_transfer_encrypt.dart';

void main() {
  runApp(const TransferEncryptExampleApp());
}

class TransferEncryptExampleApp extends StatelessWidget {
  const TransferEncryptExampleApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Transfer Encrypt Example',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF0B7285)),
        useMaterial3: true,
      ),
      home: const TransferEncryptDemoPage(),
    );
  }
}

class TransferEncryptDemoPage extends StatefulWidget {
  const TransferEncryptDemoPage({super.key});

  @override
  State<TransferEncryptDemoPage> createState() =>
      _TransferEncryptDemoPageState();
}

class _TransferEncryptDemoPageState extends State<TransferEncryptDemoPage> {
  final TextEditingController _baseUrlController =
      TextEditingController(text: 'http://localhost:8080');
  final TextEditingController _publicKeyController = TextEditingController();

  String _output = '点击下方按钮后，这里会显示请求结果。';

  @override
  void dispose() {
    _baseUrlController.dispose();
    _publicKeyController.dispose();
    super.dispose();
  }

  Future<void> _runJsonDemo() async {
    final client = TransferEncryptClient(
      baseUrl: _baseUrlController.text,
      publicKey: _publicKeyController.text,
    );
    try {
      final result = await client.postJson(
        '/api/json',
        body: <String, dynamic>{
          'name': 'flutter-client',
          'time': DateTime.now().toIso8601String(),
        },
      );
      setState(() {
        _output = _formatResult(result);
      });
    } on Object catch (error) {
      setState(() {
        _output = error.toString();
      });
    } finally {
      client.close();
    }
  }

  Future<void> _runQueryDemo() async {
    final client = TransferEncryptClient(
      baseUrl: _baseUrlController.text,
      publicKey: _publicKeyController.text,
    );
    try {
      final result = await client.get(
        '/api/query',
        params: <String, dynamic>{'name': 'flutter-query'},
      );
      setState(() {
        _output = _formatResult(result);
      });
    } on Object catch (error) {
      setState(() {
        _output = error.toString();
      });
    } finally {
      client.close();
    }
  }

  String _formatResult(Object? result) {
    if (result is Map || result is List) {
      return const JsonEncoder.withIndent('  ').convert(result);
    }
    return result?.toString() ?? '';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Transfer Encrypt Flutter Example')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: <Widget>[
          TextField(
            controller: _baseUrlController,
            decoration: const InputDecoration(
              labelText: 'Base URL',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _publicKeyController,
            minLines: 3,
            maxLines: 5,
            decoration: const InputDecoration(
              labelText: 'SM2 Public Key',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 16),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: <Widget>[
              FilledButton(
                onPressed: _runJsonDemo,
                child: const Text('POST JSON'),
              ),
              OutlinedButton(
                onPressed: _runQueryDemo,
                child: const Text('GET Query'),
              ),
            ],
          ),
          const SizedBox(height: 20),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: const Color(0xFFF1F3F5),
              borderRadius: BorderRadius.circular(16),
            ),
            child: SelectableText(_output),
          ),
        ],
      ),
    );
  }
}
