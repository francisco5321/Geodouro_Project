# YOLO Plant Detection Training

Esta pasta prepara a transição de "classificação direta da imagem inteira" para um pipeline em dois passos:

1. `YOLO` deteta se existe planta na imagem e onde ela está.
2. `MobileNetV3-Small` classifica apenas o recorte detetado.

## O que falta para treinar YOLO corretamente

Para deteção, não basta ter imagens com o nome da espécie. São precisas **anotações de bounding boxes**.

Isto significa que, para cada imagem:

- se existir planta, precisamos de pelo menos uma caixa `plant`
- se não existir planta, a imagem deve entrar como **negativa** com ficheiro de labels vazio

Sem estas anotações, o YOLO não aprende onde está a planta e não resolve bem o problema atual de falsos positivos.

## O que está pronto nesta pasta

- exportar um manifesto de imagens a partir da BD
- misturar imagens antigas do treino anterior
- montar estrutura YOLO a partir de imagens + labels YOLO
- treinar um detector binario `plant`
- gerar `data.yaml` e splits train/val/test

## Estrutura esperada

```text
training/yolo-plant-detection/
  config.example.json
  requirements.txt
  scripts/
  data/
    raw/                 # imagens sincronizadas do servidor
    manifests/           # CSVs exportados da BD
    annotations/         # labels YOLO .txt
    yolo/
      images/
        train/
        val/
        test/
      labels/
        train/
        val/
        test/
      data.yaml
  artifacts/
```

## Passo 1. Preparar ambiente

```powershell
cd training/yolo-plant-detection
py -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
Copy-Item config.example.json config.local.json
```

## Passo 2. Configurar

Edita `config.local.json` com:

- acesso `PostgreSQL`
- acesso `SSH` ao servidor remoto
- pasta local para as imagens sincronizadas
- caminho remoto de storage no servidor
- parametros de treino YOLO

## Passo 3. Exportar manifesto a partir da BD

```powershell
python scripts/export_detection_manifest.py --config config.local.json
```

Se o acesso SSH ainda não estiver funcional, o script continua com as fontes locais antigas e avisa que a parte remota falhou.

Isto pode combinar:

- imagens antigas de `images_train`, `images_val`, `images_test`
- ou, de forma mais rapida, os CSVs antigos em `training/splits/*.csv`
- imagens da BD remota `geodouro` por SSH

E gera um CSV com:

- `image_path`
- `scientific_name`
- `observation_id`
- `device_observation_id`
- `source_table`
- `source_kind`

## Passo 4. Sincronizar imagens do servidor

Se o manifesto tiver entradas da BD remota, podes puxar so essas imagens:

```powershell
python scripts/sync_remote_manifest_images.py --config config.local.json
```

O manifesto usa os caminhos relativos guardados no backend. O script copia-os para `data/raw`.

Exemplo:

```text
data/raw/
  species/...
  observations/...
```

## Passo 5. Anotar bounding boxes

Antes de anotar, podes preparar um lote inicial equilibrado:

```powershell
python scripts/prepare_annotation_batch.py --config config.local.json
```

Isto cria:

- uma pasta com imagens prontas para anotar
- um `metadata.csv` com origem e espécie
- ficheiros `.txt` vazios como placeholder

Por default, o lote inclui todas as imagens da BD remota e completa o resto com uma amostra diversa das imagens legacy.
Tambem podes usar `selection_strategy = "hard_cases"` para priorizar imagens mais dificeis do dataset antigo e `exclude_existing_batches = true` para evitar repeticoes.

Usa uma ferramenta como:

- CVAT
- Label Studio
- Roboflow Annotate

Formato esperado nesta pipeline:

- um `.txt` YOLO por imagem
- classe unica: `0 = plant`

Exemplo `imagem_001.txt`:

```text
0 0.512 0.478 0.620 0.730
```

Se a imagem for negativa, deixa o ficheiro `.txt` vazio.

### Pré-anotação automática experimental

Tambem podes gerar caixas iniciais automaticamente:

```powershell
python scripts/preannotate_with_heuristics.py --config config.local.json
```

Isto:

- analisa as imagens de um batch
- tenta encontrar a planta por segmentacao heuristica
- escreve um `.txt` YOLO inicial
- opcionalmente guarda previews em `previews/`
- pode processar so uma parte do batch por corrida (`max_images`)

Importante:

- isto serve para **acelerar revisao humana**
- não substitui anotação manual
- tens sempre de rever as caixas antes de treinar

## Passo 6. Construir dataset YOLO

```powershell
python scripts/build_yolo_dataset.py --config config.local.json
```

## Passo 7. Treinar

```powershell
python scripts/train_yolo_plant_detector.py --config config.local.json
```

O melhor checkpoint fica em:

```text
artifacts/yolo_plant_detector/
```

## Critérios minimos para uma primeira versao util

- incluir bastantes imagens negativas sem planta
- incluir plantas pequenas, desfocadas, parciais e em fundos complexos
- medir `precision`, `recall` e `mAP50`
- validar num conjunto manual de imagens "sem planta" que o detector não dispara facilmente

## Integracao Android depois do treino

Quando o detector estiver validado, o pipeline da app passa a ser:

1. carregar imagem
2. correr detector
3. se não houver caixa com score suficiente: devolver "Não é uma planta"
4. se houver caixa: recortar melhor bbox
5. classificar recorte no MobileNetV3-Small
6. opcionalmente usar a confiança do detector como gate adicional

## Nota importante

O passo mais caro aqui não é o treino em si. É a **anotação**.
Sem dataset anotado com caixas, não vale a pena começar a treinar YOLO ainda.
