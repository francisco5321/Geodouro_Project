# MobileNetV3-Small Training

Esta pasta deixa o treino do modelo dentro do projeto, sem versionar imagens pesadas.

## Estrutura

```text
training/mobilenetv3-small/
  config.example.json
  requirements.txt
  scripts/
  data/raw/          # imagens vindas do servidor, ignorado pelo git
  data/processed/    # splits CSV gerados, ignorado pelo git
  artifacts/         # checkpoints/modelo Android/labels, ignorado pelo git
```

## 1. Preparar ambiente

```bash
cd training/mobilenetv3-small
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp config.example.json config.local.json
```

No Windows PowerShell:

```powershell
cd training/mobilenetv3-small
py -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
Copy-Item config.example.json config.local.json
```

Edita `config.local.json` com o host SSH e o caminho das imagens no servidor.

## 2. Sincronizar imagens do servidor

Linux/WSL/macOS:

```bash
./scripts/sync_from_server.sh config.local.json
```

Windows PowerShell:

```powershell
.\scripts\sync_from_server.ps1 -Config config.local.json
```

Formato esperado após sincronização:

```text
data/raw/
  especie_1/
    img001.jpg
  especie_2/
    img002.jpg
```

Cada pasta dentro de `data/raw` é tratada como uma classe. Se o servidor guardar imagens noutro formato, primeiro transforma para esta estrutura ou gera CSVs com as colunas `image_path,label`.

Se o servidor guardar imagens por IDs internos e a ligação à espécie estiver na base de dados remota, usa:

```powershell
python scripts/sync_labeled_observation_images.py --config config.local.json
```

Esse helper lê `observation`, `observation_image` e `plant_species` diretamente no servidor via SSH, resolve os ficheiros remotos e organiza as imagens em `data/raw/<espécie>/...`.

Se quiseres reforçar o modelo genérico com imagens reais do GeoDouro sem reduzir o número de classes, podes criar splits híbridos:

```powershell
python scripts/build_hybrid_domain_splits.py --config config.hybrid.local.json
python scripts/train_mobilenetv3_small.py --config config.hybrid.local.json
```

Esse fluxo mantém as classes do modelo genérico e adiciona ao `train.csv` apenas as imagens do servidor cujas espécies já existem no `labels.json` base.

## 3. Gerar splits

```bash
python scripts/build_splits.py --config config.local.json
```

Isto cria:

```text
data/processed/splits/train.csv
data/processed/splits/val.csv
data/processed/splits/test.csv
```

## 3b. Usar iNaturalist 2021

Se quiseres treinar com o iNaturalist 2021, nÃ£o precisas de passar pelo `data/raw/`.
O projeto tem um helper que descarrega/prepara o dataset com `torchvision` e gera logo os CSVs no formato esperado pelo treino.

No `config.local.json`, define pelo menos:

```json
{
  "dataset": {
    "inat_root": "data/external/inat2021",
    "inat_train_version": "2021_train_mini",
    "inat_val_version": "2021_valid",
    "inat_kingdom_filter": "Plantae",
    "inat_allowed_labels_path": "",
    "inat_test_from_val_ratio": 0.5
  }
}
```

Depois corre:

```bash
python scripts/prepare_inat2021_splits.py --config config.local.json --download
```

Notas:

- `2021_train_mini` Ã© o melhor ponto de partida. O split oficial `train` tem cerca de 2.7M imagens e mais de 300 GiB.
- `2021_valid` jÃ¡ traz labels; o script divide esse split em `val.csv` e `test.csv`.
- `inat_kingdom_filter: "Plantae"` evita treinar a app com animais, fungos e outros grupos irrelevantes.
- `inat_allowed_labels_path` pode apontar para um ficheiro `.txt` com uma espÃ©cie por linha, se quiseres restringir o modelo Ã  tua lista alvo.

## 4. Treinar

```bash
python scripts/train_mobilenetv3_small.py --config config.local.json
```

O melhor checkpoint fica em:

```text
artifacts/mobilenetv3_small/mobilenetv3_small_best.pt
```

## 5. Exportar para Android

```bash
python scripts/export_torchscript_android.py --config config.local.json --copy-to-app
```

Com `--copy-to-app`, o script atualiza:

```text
app/src/main/assets/mobilenetv3_small_best_android.pt
app/src/main/assets/species_labels.txt
```

## Modelo atual encontrado fora do projeto

O treino usado anteriormente estava em:

```text
D:\Universidade\3 ANO\2 SEMESTRE\Estagio\teste modelos\MobileNetV3-Small\training\mobilenetv3_small_training.ipynb
```

O checkpoint treinado correspondente estava em:

```text
D:\Universidade\3 ANO\2 SEMESTRE\Estagio\teste modelos\MobileNetV3-Small\training\artifacts\mobilenetv3_small\mobilenetv3_small_best.pt
```
